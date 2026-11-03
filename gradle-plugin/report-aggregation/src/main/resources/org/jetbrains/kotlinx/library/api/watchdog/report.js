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
    // Prism highlights a snippet when it scrolls into view. A browser without IntersectionObserver highlights every snippet on load.
    const highlighted = new Set();
    const visible = new Set();
    const observer = typeof IntersectionObserver === "function" ? new IntersectionObserver(entries => {
        entries.forEach(entry => {
            const pre = entry.target;
            if (!entry.isIntersecting) {
                visible.delete(pre);
                return;
            }
            visible.add(pre);
            if (highlighted.has(pre)) {
                refreshPre(pre);
            } else {
                highlighted.add(pre);
                Prism.highlightElement(pre.querySelector("code"));
            }
        });
    }, {rootMargin: "50% 0px"}) : null;

    function refreshPre(pre) {
        if (!pre.classList.contains("line-numbers")) return;
        pre.querySelectorAll(".line-highlight").forEach(mark => mark.style.removeProperty("width"));
        Prism.plugins.lineNumbers.resize(pre);
        if (pre.hasAttribute("data-line")) Prism.plugins.lineHighlight.highlightLines(pre)();
    }

    let refreshScheduled = false;
    function refreshCode() {
        if (refreshScheduled) return;
        refreshScheduled = true;
        requestAnimationFrame(() => {
            refreshScheduled = false;
            const snippets = observer
                ? [...visible]
                : [...document.querySelectorAll("pre.line-numbers")].filter(pre => pre.getClientRects().length);
            snippets.forEach(refreshPre);
        });
    }

    if (observer) {
        document.querySelectorAll("pre > code.language-kotlin").forEach(code => observer.observe(code.parentElement));
    } else {
        Prism.highlightAll();
    }
    document.addEventListener("toggle", event => {
        if (event.target instanceof HTMLDetailsElement) refreshCode();
    }, true);
    window.addEventListener("resize", refreshCode);

    const controls = document.querySelector(".report-controls");
    if (!controls) return;
    const view = document.getElementById("view-mode");
    const filter = document.getElementById("diagnostic-filter");
    const projectFilter = document.getElementById("project-filter");
    const reset = document.getElementById("reset-filter");
    const status = document.getElementById("filter-summary");
    const findings = [...document.querySelectorAll(".finding")];
    const sections = [...document.querySelectorAll(".report-section")].map(section => ({
        element: section,
        groups: [...section.querySelectorAll(".diagnostic-group")].map(group => ({
            element: group,
            count: group.querySelector(".group-count"),
            list: group.querySelector(".findings"),
            findings: [...group.querySelectorAll(".finding")],
        })),
        findings: [...section.querySelectorAll(".finding")],
        tree: section.querySelector(".directory-tree"),
        filterEmpty: section.querySelector(".filter-empty"),
        treeLists: null,
        treeNodes: null,
    }));

    function setText(element, text) {
        if (element.textContent !== text) element.textContent = text;
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
        return {node, children, count};
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
        // Children come before their parent, so a filter pass sums the counts in one loop.
        section.treeNodes = [];
        function appendDirectory(directory, parent) {
            const nodes = [];
            [...directory.directories].sort(([a], [b]) => a.localeCompare(b)).forEach(([name, child]) => {
                // A chain of directories with one subdirectory and no file is one node.
                let label = name;
                while (child.files.size === 0 && child.directories.size === 1) {
                    const [[next, grandchild]] = child.directories;
                    label += "/" + next;
                    child = grandchild;
                }
                const branch = createBranch(label, false);
                const children = appendDirectory(child, branch.children);
                const node = {element: branch.node, count: branch.count, children, findings: [], visible: 0};
                section.treeNodes.push(node);
                nodes.push(node);
                parent.append(branch.node);
            });
            [...directory.files].sort(([a], [b]) => a.localeCompare(b)).forEach(([name, entries]) => {
                const branch = createBranch(name || "Unknown file", true);
                entries.sort((a, b) => Number(a.dataset.line) - Number(b.dataset.line));
                section.treeLists.push({list: branch.children, findings: entries});
                branch.children.append(...entries);
                const node = {element: branch.node, count: branch.count, children: [], findings: entries, visible: 0};
                section.treeNodes.push(node);
                nodes.push(node);
                parent.append(branch.node);
            });
            return nodes;
        }
        const list = document.createElement("ul");
        list.className = "tree-children";
        appendDirectory(root, list);
        section.tree.append(list);
    }

    function matches(finding) {
        if (filter.value !== "" && !finding.dataset.diagnostics.split(" ").includes(filter.value)) return false;
        return !projectFilter || projectFilter.value === "" || finding.dataset.project === projectFilter.value;
    }

    function updateFilter() {
        let shown = 0;
        findings.forEach(finding => {
            const hidden = !matches(finding);
            if (finding.hidden !== hidden) finding.hidden = hidden;
            if (!hidden) shown++;
        });
        sections.forEach(section => {
            section.groups.forEach(group => {
                const count = group.findings.filter(finding => !finding.hidden).length;
                group.element.hidden = count === 0;
                setText(group.count, "(" + count + ")");
            });
            if (!section.tree) return;
            if (section.treeNodes) {
                section.treeNodes.forEach(node => {
                    node.visible = node.children.length
                        ? node.children.reduce((sum, child) => sum + child.visible, 0)
                        : node.findings.filter(finding => !finding.hidden).length;
                    node.element.hidden = node.visible === 0;
                    setText(node.count, "(" + node.visible + ")");
                });
            }
            section.filterEmpty.hidden = section.findings.some(finding => !finding.hidden);
        });
        const active = filter.value !== "" || (projectFilter !== null && projectFilter.value !== "");
        setText(status, active ? "Showing " + shown + " of " + findings.length + " findings." : "");
        reset.disabled = !active;
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
    if (projectFilter) projectFilter.addEventListener("change", updateFilter);
    reset.addEventListener("click", () => {
        filter.value = "";
        if (projectFilter) projectFilter.value = "";
        updateFilter();
    });
    controls.hidden = false;
})();
