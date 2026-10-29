import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {JSDOM, VirtualConsole} from "jsdom";

const fixtures = new URL("../../../build/html-report-fixtures/", import.meta.url);

function load(t, name = "mixed", scripts = true, lazy = false) {
    const errors = [];
    const frames = [];
    const observers = [];
    const console = new VirtualConsole();
    console.on("jsdomError", error => errors.push(error));
    const dom = new JSDOM(readFileSync(new URL(name + ".html", fixtures), "utf8"), {
        runScripts: scripts ? "dangerously" : "outside-only",
        pretendToBeVisual: true,
        virtualConsole: console,
        beforeParse(window) {
            window.requestAnimationFrame = callback => frames.push(callback);
            if (lazy) {
                // jsdom has no IntersectionObserver. The fake records the observed elements and lets a test fire the callback.
                window.IntersectionObserver = class {
                    constructor(callback, options) {
                        this.callback = callback;
                        this.options = options;
                        this.targets = [];
                        observers.push(this);
                    }
                    observe(target) { this.targets.push(target); }
                    unobserve() {}
                    disconnect() {}
                    intersect(target, isIntersecting) { this.callback([{target, isIntersecting}], this); }
                };
            }
        },
    });
    const {window} = dom;
    const {document} = window;
    t.after(() => {
        window.close();
        assert.deepEqual(errors, []);
    });
    return {
        window, document, frames, observers,
        change(id, value) {
            const select = document.getElementById(id);
            select.value = value;
            select.dispatchEvent(new window.Event("change"));
        },
        flushFrames() {
            for (const callback of frames.splice(0)) callback();
        },
    };
}

const visibleFindings = document => [...document.querySelectorAll(".finding")].filter(finding => !finding.hidden);
const locations = findings => findings.map(finding => finding.querySelector(".location code").textContent);

test("reports remain readable when JavaScript is disabled", t => {
    const {document} = load(t, "mixed", false);
    assert.equal(document.querySelector(".report-controls").hidden, true);
    assert.equal(visibleFindings(document).length, 6);
    assert.equal(document.querySelectorAll(".diagnostic-group[open]").length, 5);
    assert.equal(document.querySelector("pre code").textContent, 'public data class Api(\n    val text: String = "😀<&>",\n)\n');
    assert.equal(document.querySelector(".reported-element"), null);
});

test("empty reports initialize without controls or errors", t => {
    const {document, window} = load(t, "empty");
    window.Prism.highlightAll();
    assert.equal(document.querySelector(".report-controls"), null);
    assert.equal(document.querySelectorAll(".finding").length, 0);
    assert.equal(document.querySelectorAll(".empty").length, 2);
});

test("controls enable with an unfiltered diagnostic view", t => {
    const {document} = load(t);
    assert.equal(document.querySelector(".report-controls").hidden, false);
    assert.equal(document.getElementById("view-mode").value, "diagnostic");
    assert.equal(document.getElementById("diagnostic-filter").value, "");
    assert.equal(document.getElementById("project-filter").value, "");
    assert.equal(document.getElementById("reset-filter").disabled, true);
    assert.equal(document.getElementById("filter-summary").textContent, "");
    assert.equal(visibleFindings(document).length, 6);
});

test("diagnostic filters match individual checks in combined applied annotations", t => {
    const report = load(t);
    const {document, change} = report;
    change("diagnostic-filter", "STATEFUL_CLASS_WITHOUT_HASH_CODE");
    assert.equal(visibleFindings(document).length, 1);
    assert.equal(visibleFindings(document)[0].querySelector(".fix-status").textContent, "Applied");
    assert.equal(document.getElementById("filter-summary").textContent, "Showing 1 of 6 findings.");
    assert.equal(document.getElementById("reset-filter").disabled, false);
    assert.equal(document.querySelector("#not-applied").parentElement.querySelector(".filter-empty").hidden, false);
    assert.equal(document.querySelector("#applied").parentElement.querySelector(".filter-empty").hidden, true);
    const groups = [...document.querySelectorAll(".diagnostic-group")].filter(group => !group.hidden);
    assert.equal(groups.length, 1);
    assert.equal(groups[0].querySelector(".group-count").textContent, "(1)");
    change("diagnostic-filter", "DATA_CLASS_PUBLIC_API");
    assert.equal(visibleFindings(document).length, 2);
    assert.equal(document.getElementById("filter-summary").textContent, "Showing 2 of 6 findings.");
    document.getElementById("reset-filter").click();
    assert.equal(visibleFindings(document).length, 6);
    assert.equal(document.getElementById("diagnostic-filter").value, "");
    assert.equal(document.getElementById("filter-summary").textContent, "");
    assert.equal(document.getElementById("reset-filter").disabled, true);
});

test("project filters narrow findings together with the diagnostic filter and reset with it", t => {
    const {document, change} = load(t);
    const findings = [...document.querySelectorAll(".finding")];
    const options = [...document.querySelectorAll("#project-filter option")];
    assert.deepEqual(options.map(option => option.value), ["", ":", ":library"]);
    assert.deepEqual(options.map(option => option.textContent), ["All projects", ":", ":library"]);
    const expected = (project, diagnostic) => findings.filter(finding =>
        (!project || finding.dataset.project === project) && (!diagnostic || finding.dataset.diagnostics.split(" ").includes(diagnostic)));
    change("project-filter", ":");
    assert.deepEqual(locations(visibleFindings(document)), ["src/Api.kt:2"]);
    assert.equal(document.getElementById("filter-summary").textContent, "Showing 1 of 6 findings.");
    assert.equal(document.getElementById("reset-filter").disabled, false);
    change("project-filter", ":library");
    assert.equal(visibleFindings(document).length, 5);
    assert.equal(document.querySelector("#not-applied").parentElement.querySelector(".filter-empty").hidden, true);
    change("diagnostic-filter", "DATA_CLASS_PUBLIC_API");
    assert.deepEqual(visibleFindings(document), expected(":library", "DATA_CLASS_PUBLIC_API"));
    assert.equal(visibleFindings(document).length, 1);
    change("view-mode", "directory");
    assert.deepEqual(visibleFindings(document), expected(":library", "DATA_CLASS_PUBLIC_API"));
    for (const node of document.querySelectorAll(".tree-node")) {
        const count = [...node.querySelectorAll(".finding")].filter(finding => !finding.hidden).length;
        assert.equal(node.hidden, count === 0);
        assert.equal(node.querySelector(".group-count").textContent, "(" + count + ")");
    }
    document.getElementById("reset-filter").click();
    assert.equal(document.getElementById("project-filter").value, "");
    assert.equal(document.getElementById("diagnostic-filter").value, "");
    assert.equal(visibleFindings(document).length, 6);
    assert.equal(document.getElementById("filter-summary").textContent, "");
});

test("single-project reports have no project filter", t => {
    const {document, change} = load(t, "duplicates");
    assert.equal(document.getElementById("project-filter"), null);
    assert.equal(document.querySelector(".finding").dataset.project, ":library");
    change("diagnostic-filter", "FUTURE_CHECK");
    assert.equal(visibleFindings(document).length, 1);
    document.getElementById("reset-filter").click();
    assert.equal(visibleFindings(document).length, 5);
});

test("unknown annotations use the unknown diagnostic filter", t => {
    const {document, change} = load(t);
    change("diagnostic-filter", "unknown");
    assert.deepEqual(locations(visibleFindings(document)), ['src/<script>&".kt:1']);
    assert.equal(visibleFindings(document)[0].querySelector(".group-annotation").textContent, "@IntentionallyCustom");
});

test("unknown diagnostic names with spaces punctuation Unicode or no text remain filterable", t => {
    const {document, change} = load(t, "unusual-diagnostics");
    const options = [...document.querySelectorAll("#diagnostic-filter option")].slice(1);
    assert.equal(options.length, 9);
    assert.equal(new Set(options.map(option => option.value)).size, 9);
    assert.ok(options.every(option => option.value !== "" && option.textContent !== ""));
    for (const view of ["diagnostic", "directory"]) {
        change("view-mode", view);
        for (const option of options) {
            change("diagnostic-filter", option.value);
            assert.equal(visibleFindings(document).length, 1, option.value);
            assert.equal(visibleFindings(document)[0].dataset.diagnostics, option.value);
        }
    }
});

test("directory views preserve nodes and restore original diagnostic order", t => {
    const {document, change} = load(t);
    const findings = [...document.querySelectorAll(".finding")];
    const groups = [...document.querySelectorAll(".diagnostic-group")];
    const original = groups.map(group => [...group.querySelectorAll(".finding")]);
    groups[0].open = false;
    change("view-mode", "directory");
    assert.equal(document.querySelectorAll(".finding").length, findings.length);
    assert.equal(document.querySelectorAll(".diagnostic-groups:not([hidden])").length, 0);
    assert.equal(document.querySelectorAll(".directory-tree[hidden]").length, 0);
    assert.equal(document.querySelectorAll(".tree-file[open]").length, 0);
    assert.equal(document.querySelectorAll(".tree-branch:not(.tree-file):not([open])").length, 0);
    assert.equal(document.querySelector(".finding-heading").closest("section").dataset.view, "directory");
    const firstFile = document.querySelector(".tree-file");
    firstFile.open = true;
    for (let index = 0; index < 4; index++) {
        change("view-mode", "diagnostic");
        groups.forEach((group, index) => assert.deepEqual([...group.querySelectorAll(".finding")], original[index]));
        assert.equal(groups[0].open, false);
        assert.equal(document.querySelectorAll(".directory-tree:not([hidden])").length, 0);
        change("view-mode", "directory");
        assert.equal(firstFile.open, true);
        assert.equal(document.querySelectorAll(".finding").length, findings.length);
        findings.forEach(finding => assert.equal(document.contains(finding), true));
    }
});

test("directory branches separate projects and sort findings numerically by source line", t => {
    const {document, change} = load(t);
    change("view-mode", "directory");
    const files = [...document.querySelectorAll("#not-applied ~ .directory-tree .tree-file")];
    const apiFiles = files.filter(file => file.querySelector(".tree-name").textContent === "Api.kt");
    assert.equal(apiFiles.length, 2);
    const moduleFile = apiFiles.find(file => file.querySelectorAll(".finding").length === 2);
    assert.deepEqual(locations([...moduleFile.querySelectorAll(".finding")]), ["src/Api.kt:3", "src/Api.kt:12"]);
    const top = document.querySelector("#not-applied ~ .directory-tree > ul");
    assert.deepEqual([...top.children].map(node => node.querySelector(".tree-name").textContent), ["modules/library/src", "src"]);
    const merged = top.children[0].querySelector(":scope > details > .tree-children");
    assert.deepEqual([...merged.children].map(node => node.querySelector(".tree-name").textContent), ["z", "Api.kt"]);
    assert.equal(merged.children[0].querySelector(".tree-file .tree-name").textContent, "Other.kt");
    assert.equal(document.querySelectorAll(".directory-tree script, .directory-tree img").length, 0);
    assert.equal([...document.querySelectorAll(".tree-name")].some(name => name.textContent === '<script>&".kt'), true);
});

test("duplicate findings at the same location survive directory grouping and filters", t => {
    const {document, change} = load(t, "duplicates");
    const findings = [...document.querySelectorAll(".finding")];
    change("view-mode", "directory");
    assert.equal(document.querySelectorAll(".tree-file").length, 2);
    assert.equal(document.querySelectorAll(".finding").length, 5);
    change("diagnostic-filter", "DATA_CLASS_PUBLIC_API");
    assert.equal(visibleFindings(document).length, 2);
    change("view-mode", "diagnostic");
    document.getElementById("reset-filter").click();
    assert.equal(visibleFindings(document).length, 5);
    findings.forEach(finding => assert.equal(document.contains(finding), true));
});

test("directory filters hide empty ancestors and keep counts correct across view changes", t => {
    const {document, change} = load(t);
    change("diagnostic-filter", "UNDOCUMENTED_PUBLIC_API");
    change("view-mode", "directory");
    for (let index = 0; index < 3; index++) {
        assert.equal(visibleFindings(document).length, 1);
        for (const node of document.querySelectorAll(".tree-node")) {
            const count = [...node.querySelectorAll(".finding")].filter(finding => !finding.hidden).length;
            assert.equal(node.hidden, count === 0);
            assert.equal(node.querySelector(".group-count").textContent, "(" + count + ")");
        }
        change("view-mode", "diagnostic");
        assert.equal(visibleFindings(document).length, 1);
        change("view-mode", "directory");
    }
    document.getElementById("reset-filter").click();
    assert.equal(visibleFindings(document).length, 6);
    assert.equal(document.querySelectorAll(".tree-node[hidden]").length, 0);
});

for (const name of ["applied-only", "not-applied-only"]) {
    test(name + " supports both views and filtering with an empty section", t => {
        const {document, change} = load(t, name);
        const count = document.querySelectorAll(".finding").length;
        assert.equal(document.querySelectorAll(".empty").length, 1);
        change("view-mode", "directory");
        change("diagnostic-filter", name === "applied-only" ? "unknown" : "FUTURE_CHECK");
        assert.equal(visibleFindings(document).length, 1);
        change("view-mode", "diagnostic");
        document.getElementById("reset-filter").click();
        assert.equal(visibleFindings(document).length, count);
    });
}

test("directory views tolerate missing filenames and malformed source locations", t => {
    const {document, change} = load(t, "malformed");
    change("view-mode", "directory");
    assert.equal(visibleFindings(document).length, 5);
    assert.equal(document.querySelectorAll(".directory-tree .finding").length, 5);
    assert.equal([...document.querySelectorAll(".tree-file .tree-name")].every(label => label.textContent.length > 0), true);
    const unknown = [...document.querySelectorAll(".tree-file")].filter(file => file.querySelector(".tree-name").textContent === "Unknown file");
    assert.equal(unknown.length, 2);
    assert.deepEqual(unknown.map(file => file.querySelectorAll(".finding").length).sort(), [1, 2]);
    change("view-mode", "diagnostic");
    assert.equal(document.querySelectorAll(".diagnostic-groups .finding").length, 5);
});

test("seeded view and filter changes never lose or duplicate findings", t => {
    const {document, change} = load(t);
    const findings = [...document.querySelectorAll(".finding")];
    const diagnostics = [...document.querySelectorAll("#diagnostic-filter option")].map(option => option.value);
    let seed = 0x5eed2026;
    const next = () => (seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0);
    const projects = [...document.querySelectorAll("#project-filter option")].map(option => option.value);
    for (let iteration = 0; iteration < 128; iteration++) {
        switch (next() % 4) {
            case 0: change("view-mode", next() % 2 ? "directory" : "diagnostic"); break;
            case 1: change("project-filter", projects[next() % projects.length]); break;
            default: change("diagnostic-filter", diagnostics[next() % diagnostics.length]);
        }
        const selected = document.getElementById("diagnostic-filter").value;
        const project = document.getElementById("project-filter").value;
        const expected = findings.filter(finding =>
            (!selected || finding.dataset.diagnostics.split(" ").includes(selected)) && (!project || finding.dataset.project === project));
        assert.equal(document.querySelectorAll(".finding").length, findings.length, "iteration=" + iteration);
        assert.equal(visibleFindings(document).length, expected.length, "iteration=" + iteration);
        findings.forEach(finding => assert.equal(document.contains(finding), true));
    }
});

test("Prism preserves source text and underlines the exact reported UTF-16 range", t => {
    const {window, document, change} = load(t);
    const pre = document.querySelector("pre[data-range-start]");
    const code = pre.querySelector("code");
    const original = code.textContent;
    const expected = original.slice(Number(pre.dataset.rangeStart), Number(pre.dataset.rangeEnd));
    for (let iteration = 0; iteration < 3; iteration++) {
        window.Prism.highlightElement(code);
        assert.equal(code.textContent, original);
        assert.equal([...code.querySelectorAll(".reported-error")].map(span => span.textContent).join(""), expected);
        assert.ok(code.querySelector(".token"));
        assert.equal(code.querySelectorAll(".reported-element .reported-element").length, 0);
        assert.equal(code.querySelectorAll(".line-numbers-rows").length, 1);
        assert.equal(pre.querySelectorAll(".line-highlight").length, 1);
        change("view-mode", iteration % 2 ? "directory" : "diagnostic");
    }
});

test("range highlighting crosses syntax tokens and lines without changing source text", t => {
    const {document, window} = load(t, "empty");
    const source = 'val text = "😀<&>"\n// café e\u0301\nval next = 42';
    for (const severity of ["error", "warning"]) {
        for (const [start, end] of [[0, source.length], [4, 8], [10, 18], [16, 30]]) {
            const pre = document.createElement("pre");
            pre.dataset.rangeStart = start;
            pre.dataset.rangeEnd = end;
            pre.dataset.severity = severity;
            const code = document.createElement("code");
            code.className = "language-kotlin";
            code.textContent = source;
            pre.append(code);
            document.body.append(pre);
            window.Prism.highlightElement(code);
            assert.equal(code.textContent, source);
            assert.equal([...code.querySelectorAll(".reported-" + severity)].map(span => span.textContent).join(""), source.slice(start, end));
            pre.remove();
        }
    }
});

test("without IntersectionObserver the page highlights every snippet on load", t => {
    const {document} = load(t);
    const blocks = [...document.querySelectorAll("pre code")];
    assert.ok(blocks.length > 1);
    assert.ok(blocks.every(code => code.querySelector(".token")));
    assert.equal(document.querySelectorAll("pre.line-numbers .line-numbers-rows").length, document.querySelectorAll("pre.line-numbers").length);
});

test("with IntersectionObserver the page highlights and refreshes only snippets in view", t => {
    const {document, window, observers, flushFrames} = load(t, "mixed", true, true);
    const [observer] = observers;
    const snippets = [...document.querySelectorAll("pre")];
    assert.deepEqual(observer.targets, snippets);
    assert.equal(document.querySelector(".token"), null);
    const [first, second] = snippets;
    observer.intersect(first, true);
    assert.ok(first.querySelector(".token"));
    assert.equal(second.querySelector(".token"), null);
    const resized = [];
    window.Prism.plugins.lineNumbers.resize = element => resized.push(element);
    window.Prism.plugins.lineHighlight.highlightLines = () => () => {};
    window.dispatchEvent(new window.Event("resize"));
    flushFrames();
    assert.deepEqual(resized, [first]);
    observer.intersect(first, true);
    assert.deepEqual(resized, [first, first]);
    assert.equal(first.querySelectorAll(".line-numbers-rows").length, 1);
    observer.intersect(first, false);
    resized.length = 0;
    window.dispatchEvent(new window.Event("resize"));
    flushFrames();
    assert.deepEqual(resized, []);
    observer.intersect(second, true);
    assert.ok(second.querySelector(".token"));
});

test("code blocks without source ranges get syntax colors without reported element marks", t => {
    const {document, window} = load(t);
    const blocks = [...document.querySelectorAll("pre code")];
    const sources = blocks.map(code => code.textContent);
    window.Prism.highlightAll();
    blocks.forEach((code, index) => assert.equal(code.textContent, sources[index]));
    const applied = document.querySelector("#applied ~ .diagnostic-groups pre code");
    assert.ok(applied.querySelector(".token"));
    assert.equal(applied.querySelectorAll(".reported-element").length, 0);
    const detached = document.createElement("code");
    detached.className = "language-kotlin";
    detached.textContent = "val x = 1";
    window.Prism.highlightElement(detached);
    assert.equal(detached.textContent, "val x = 1");
    assert.equal(detached.querySelectorAll(".reported-element").length, 0);
});

test("invalid highlight ranges and severities leave code readable", t => {
    const {document, window} = load(t, "empty");
    for (const [start, end, severity] of [
        [-1, 2, "error"], [0, 100, "error"], [1, 1, "error"], [3, 2, "error"],
        ["NaN", 2, "error"], [0.5, 2, "error"], [0, "Infinity", "error"],
        [0, 2, "info"], [0, 2, ""], [0, 2, 'error" onclick="alert(1)'],
    ]) {
        const pre = document.createElement("pre");
        pre.dataset.rangeStart = start;
        pre.dataset.rangeEnd = end;
        pre.dataset.severity = severity;
        const code = document.createElement("code");
        code.className = "language-kotlin";
        code.textContent = "val x = 1";
        pre.append(code);
        document.body.append(pre);
        window.Prism.highlightElement(code);
        assert.equal(code.textContent, "val x = 1");
        assert.equal(code.querySelectorAll(".reported-element").length, 0);
        pre.remove();
    }
});

test("code refreshes batch resize and disclosure events and skip hidden snippets", t => {
    const {document, window, frames, flushFrames} = load(t);
    const pre = document.querySelector("pre.line-numbers");
    pre.getClientRects = () => [{width: 200}];
    const resized = [];
    const highlighted = [];
    window.Prism.plugins.lineNumbers.resize = element => resized.push(element);
    window.Prism.plugins.lineHighlight.highlightLines = element => () => highlighted.push(element);
    window.dispatchEvent(new window.Event("resize"));
    document.querySelector("details").dispatchEvent(new window.Event("toggle"));
    window.dispatchEvent(new window.Event("resize"));
    assert.equal(frames.length, 1);
    resized.length = highlighted.length = 0;
    flushFrames();
    assert.deepEqual(resized, [pre]);
    assert.deepEqual(highlighted, [pre]);
    pre.removeAttribute("data-line");
    window.dispatchEvent(new window.Event("resize"));
    resized.length = highlighted.length = 0;
    flushFrames();
    assert.deepEqual(resized, [pre]);
    assert.equal(highlighted.length, 0);
    pre.getClientRects = () => [];
    window.dispatchEvent(new window.Event("resize"));
    resized.length = highlighted.length = 0;
    flushFrames();
    assert.equal(resized.length, 0);
    assert.equal(highlighted.length, 0);
});
