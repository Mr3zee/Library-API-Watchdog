Prism.hooks.add("after-highlight", function (environment) {
    const code = environment.element;
    const pre = code.parentElement;
    if (!pre || !pre.matches("pre[data-range-start][data-range-end]")) return;
    const severity = pre.dataset.severity;
    if (severity !== "error" && severity !== "warning") return;
    const start = Number(pre.dataset.rangeStart);
    const end = Number(pre.dataset.rangeEnd);
    if (!Number.isInteger(start) || !Number.isInteger(end) || start < 0 || end <= start || end > environment.code.length) return;

    const walker = document.createTreeWalker(code, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    let offset = 0;
    for (const node of nodes) {
        const text = node.textContent;
        const from = Math.max(0, start - offset);
        const to = Math.min(text.length, end - offset);
        offset += text.length;
        if (from >= to) continue;
        const fragment = document.createDocumentFragment();
        fragment.append(text.slice(0, from));
        const highlight = document.createElement("span");
        highlight.className = "reported-element reported-" + severity;
        highlight.textContent = text.slice(from, to);
        fragment.append(highlight, text.slice(to));
        node.replaceWith(fragment);
    }
});

(function () {
    const controls = document.querySelector(".report-controls");
    if (!controls) return;
    const view = document.getElementById("view-mode");
    const filter = document.getElementById("diagnostic-filter");
    const reset = document.getElementById("reset-filter");
    const status = document.getElementById("filter-summary");
    const findings = [...document.querySelectorAll(".finding")];
    const sections = [...document.querySelectorAll(".report-section")].map(section => ({
        element: section,
        groups: [...section.querySelectorAll(".diagnostic-group")].map(group => ({
            element: group,
            list: group.querySelector(".findings"),
            findings: [...group.querySelectorAll(".finding")],
        })),
        findings: [...section.querySelectorAll(".finding")],
        tree: section.querySelector(".directory-tree"),
        treeLists: null,
    }));

    let refreshScheduled = false;
    function refreshCode() {
        if (refreshScheduled) return;
        refreshScheduled = true;
        requestAnimationFrame(() => {
            refreshScheduled = false;
            document.querySelectorAll("pre.line-numbers").forEach(pre => {
                if (!pre.getClientRects().length) return;
                pre.querySelectorAll(".line-highlight").forEach(mark => mark.style.removeProperty("width"));
                Prism.plugins.lineNumbers.resize(pre);
                if (pre.hasAttribute("data-line")) Prism.plugins.lineHighlight.highlightLines(pre)();
            });
        });
    }

    function createBranch(name, file) {
        const node = document.createElement("li");
        node.className = "tree-node";
        const details = document.createElement("details");
        details.className = "tree-branch" + (file ? " tree-file" : "");
        details.open = !file;
        const summary = document.createElement("summary");
        const label = document.createElement("span");
        label.className = "tree-name";
        label.textContent = name;
        const count = document.createElement("span");
        count.className = "group-count";
        summary.append(label, " ", count);
        const children = document.createElement("ul");
        children.className = file ? "findings" : "tree-children";
        details.append(summary, children);
        node.append(details);
        return {node, children};
    }

    function buildTree(section) {
        const root = {directories: new Map(), files: new Map()};
        section.findings.forEach(finding => {
            const parts = finding.dataset.path.split("/").filter(Boolean);
            const filename = parts.pop() || "";
            let directory = root;
            parts.forEach(part => {
                if (!directory.directories.has(part)) {
                    directory.directories.set(part, {directories: new Map(), files: new Map()});
                }
                directory = directory.directories.get(part);
            });
            if (!directory.files.has(filename)) directory.files.set(filename, []);
            directory.files.get(filename).push(finding);
        });
        section.treeLists = [];
        function appendDirectory(directory, parent) {
            [...directory.directories].sort(([a], [b]) => a.localeCompare(b)).forEach(([name, child]) => {
                const branch = createBranch(name, false);
                appendDirectory(child, branch.children);
                parent.append(branch.node);
            });
            [...directory.files].sort(([a], [b]) => a.localeCompare(b)).forEach(([name, entries]) => {
                const branch = createBranch(name || "Unknown file", true);
                entries.sort((a, b) => Number(a.dataset.line) - Number(b.dataset.line));
                section.treeLists.push({list: branch.children, findings: entries});
                branch.children.append(...entries);
                parent.append(branch.node);
            });
        }
        const list = document.createElement("ul");
        list.className = "tree-children";
        appendDirectory(root, list);
        section.tree.append(list);
    }

    function updateFilter() {
        findings.forEach(finding => {
            finding.hidden = filter.value !== "" && !finding.dataset.diagnostics.split(" ").includes(filter.value);
        });
        sections.forEach(section => {
            section.groups.forEach(group => {
                const count = group.findings.filter(finding => !finding.hidden).length;
                group.element.hidden = count === 0;
                group.element.querySelector(".group-count").textContent = "(" + count + ")";
            });
            if (section.tree) {
                section.tree.querySelectorAll(".tree-node").forEach(node => {
                    const count = [...node.querySelectorAll(".finding")].filter(finding => !finding.hidden).length;
                    node.hidden = count === 0;
                    node.querySelector(".group-count").textContent = "(" + count + ")";
                });
                section.element.querySelector(".filter-empty").hidden = section.findings.some(finding => !finding.hidden);
            }
        });
        const count = findings.filter(finding => !finding.hidden).length;
        status.textContent = filter.value ? "Showing " + count + " of " + findings.length + " findings." : "";
        reset.disabled = filter.value === "";
        refreshCode();
    }

    function updateView() {
        const directory = view.value === "directory";
        sections.forEach(section => {
            if (!section.tree) return;
            if (directory) {
                if (section.treeLists === null) buildTree(section);
                else section.treeLists.forEach(file => file.list.append(...file.findings));
            } else {
                section.groups.forEach(group => group.list.append(...group.findings));
            }
            section.element.dataset.view = view.value;
            section.element.querySelector(".diagnostic-groups").hidden = directory;
            section.tree.hidden = !directory;
        });
        updateFilter();
    }

    view.addEventListener("change", updateView);
    filter.addEventListener("change", updateFilter);
    reset.addEventListener("click", () => {
        filter.value = "";
        updateFilter();
    });
    document.addEventListener("toggle", event => {
        if (event.target instanceof HTMLDetailsElement) refreshCode();
    }, true);
    window.addEventListener("resize", refreshCode);
    controls.hidden = false;
})();
