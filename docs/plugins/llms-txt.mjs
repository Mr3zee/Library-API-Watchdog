// Writes the Markdown form of the site into the build output. The files are:
//
//   - `llms.txt`: the page index in sidebar order (https://llmstxt.org/).
//   - `llms-full.txt`: every page, in the same order.
//   - `<page>.md` next to each `<page>.html`: one page.
//
// A page comes from its Markdown source, not from the built HTML, so a code sample keeps its
// language and its diagnostics. The sample shows the lines of the Focused view, and a `!diag`
// annotation becomes a comment with the diagnostic name above the line it marks.
import fs from 'node:fs/promises';
import path from 'node:path';
import {toString} from 'mdast-util-to-string';
import remarkDirective from 'remark-directive';
import remarkFrontmatter from 'remark-frontmatter';
import remarkGfm from 'remark-gfm';
import remarkParse from 'remark-parse';
import remarkStringify from 'remark-stringify';
import {unified} from 'unified';
import {visit} from 'unist-util-visit';
import {kotlinVersion, projectVersion, remarkProjectVersion} from './remark-project-version.mjs';

const DOCS_PLUGIN = 'docusaurus-plugin-content-docs';
const TOP_LEVEL_SECTION = 'Documentation';

// `// !name(from:to)[/range/flags] query`. Code Hike counts `from` and `to` from the next code line.
const ANNOTATION_COMMENT = /^(\s*)\/\/ !([a-z-]+)(?:\((\d+):(\d+)\))?(?:\[\/(.+)\/([a-z]*)\])?(?:\s+(.*?))?\s*$/;
const DIAGNOSTIC_NAME = /^[A-Z][A-Z0-9_]*/;

export default function llmsTxt(context) {
  const {siteConfig, siteDir} = context;
  const siteUrl = `${siteConfig.url}${siteConfig.baseUrl}`;
  let pages = [];

  return {
    name: 'llms-txt',

    allContentLoaded({allContent}) {
      const docs = allContent[DOCS_PLUGIN]?.default;
      if (!docs) throw new Error(`The llms-txt plugin needs the ${DOCS_PLUGIN} plugin.`);
      pages = collectPages(docs.loadedVersions[0], {siteDir, siteUrl, baseUrl: siteConfig.baseUrl});
    },

    async postBuild({outDir}) {
      const versions = {libraryVersion: projectVersion, kotlin: kotlinVersion};
      const rendered = [];
      for (const page of pages) {
        const source = await fs.readFile(page.sourcePath, 'utf8');
        const {markdown, description} = renderPage(source, {page, pages, siteUrl, versions});
        page.description ??= description;
        const file = path.join(outDir, page.markdownPath);
        await fs.mkdir(path.dirname(file), {recursive: true});
        await fs.writeFile(file, markdown);
        rendered.push(markdown);
      }
      await fs.writeFile(path.join(outDir, 'llms.txt'), renderIndex(pages, siteConfig));
      await fs.writeFile(path.join(outDir, 'llms-full.txt'), rendered.join('\n'));
    },
  };
}

/**
 * The docs of a loaded version in sidebar order, each with its section and its Markdown URL. The
 * description is the `description` front matter. Without one, `renderPage` gives it.
 */
export function collectPages(version, {siteDir, siteUrl, baseUrl}) {
  const docsById = new Map(version.docs.map((doc) => [doc.id, doc]));
  const pages = [];

  const addDoc = (id, section) => {
    const doc = docsById.get(id);
    if (!doc) throw new Error(`The sidebar lists an unknown doc ${id}.`);
    // The category index of `checks/java-interop/` is `checks/java-interop.html`.
    const route = doc.permalink.slice(baseUrl.length).replace(/\/$/, '');
    const markdownPath = `${route === '' ? 'index' : route}.md`;
    pages.push({
      id,
      section,
      title: doc.title,
      description: doc.frontMatter.description,
      sourcePath: path.join(siteDir, doc.source.replace(/^@site\//, '')),
      markdownPath,
      url: `${siteUrl}${markdownPath}`,
    });
  };

  const walk = (items, section) => {
    for (const item of items) {
      if (item.type === 'doc' || item.type === 'ref') {
        addDoc(item.id, section);
      } else if (item.type === 'category') {
        const label = section === TOP_LEVEL_SECTION ? item.label : `${section}: ${item.label}`;
        if (item.link?.type === 'doc') addDoc(item.link.id, label);
        walk(item.items, label);
      }
    }
  };

  for (const sidebar of Object.values(version.sidebars)) walk(sidebar, TOP_LEVEL_SECTION);
  return pages;
}

/** The `llms.txt` text: the site title, the tagline, and one list of pages for each section. */
export function renderIndex(pages, {title, tagline}) {
  const sections = new Map();
  for (const page of pages) {
    if (!sections.has(page.section)) sections.set(page.section, []);
    sections.get(page.section).push(page);
  }

  const lines = [`# ${title}`, '', `> ${tagline}`];
  for (const [section, sectionPages] of sections) {
    lines.push('', `## ${section}`, '');
    for (const page of sectionPages) {
      const description = page.description ? `: ${page.description}` : '';
      lines.push(`- [${page.title}](${page.url})${description}`);
    }
  }
  return `${lines.join('\n')}\n`;
}

/**
 * The Markdown of one page, with absolute links and the code samples of the Focused view. The
 * description for `llms.txt` is the first top-level paragraph with text.
 */
export function renderPage(source, {page, pages, siteUrl, versions}) {
  const resolveUrl = (url) => resolveLink(url, {page, pages, siteUrl});
  const result = {description: undefined};
  const markdown = unified()
    .use(remarkParse)
    .use(remarkFrontmatter)
    .use(remarkGfm)
    .use(remarkDirective)
    .use(remarkProjectVersion, versions)
    .use(remarkLlmsPage, {title: page.title, resolveUrl, result})
    .use(remarkStringify, {bullet: '-', emphasis: '_', fences: true})
    .processSync(source)
    .toString();
  return {markdown, description: result.description};
}

function remarkLlmsPage({title, resolveUrl, result}) {
  return (tree) => {
    tree.children = tree.children.filter((node) => node.type !== 'yaml');
    if (!tree.children.some((node) => node.type === 'heading' && node.depth === 1)) {
      tree.children.unshift({type: 'heading', depth: 1, children: [{type: 'text', value: title}]});
    }
    const paragraphs = tree.children.filter((node) => node.type === 'paragraph');
    const described = paragraphs.map((node) =>
      toString(node, {includeImageAlt: false}).replace(/\s+/g, ' ').trim(),
    );
    result.description = described.find((text) => text !== '');
    visit(tree, 'containerDirective', (node) => {
      admonitionToBlockquote(node);
    });
    visit(tree, ['link', 'definition', 'image'], (node) => {
      node.url = resolveUrl(node.url);
    });
    visit(tree, 'code', (node) => {
      node.value = renderSample(node.value);
    });
  };
}

// `:::tip` becomes a blockquote. Its first paragraph starts with the bold label.
function admonitionToBlockquote(node) {
  const label = {type: 'strong', children: [{type: 'text', value: capitalize(node.name)}]};
  const [first] = node.children;
  if (first?.type === 'paragraph') {
    first.children.unshift(label, {type: 'text', value: ': '});
  } else {
    node.children.unshift({type: 'paragraph', children: [label]});
  }
  node.type = 'blockquote';
  delete node.name;
  delete node.attributes;
}

function capitalize(word) {
  return word.charAt(0).toUpperCase() + word.slice(1);
}

/**
 * An absolute URL for a link of a page. A relative `.md` link goes to the Markdown of that page,
 * and a root-relative path goes to the site route.
 */
export function resolveLink(url, {page, pages, siteUrl}) {
  if (/^[a-z][a-z0-9+.-]*:/i.test(url) || url.startsWith('#')) return url;
  if (url.startsWith('/')) return `${siteUrl}${url.slice(1)}`;

  const [target, anchor] = splitAnchor(url);
  const sourcePath = path.resolve(path.dirname(page.sourcePath), target);
  const linked = pages.find((candidate) => candidate.sourcePath === sourcePath);
  if (!linked) throw new Error(`The link ${url} in ${page.sourcePath} points to no page.`);
  return `${linked.url}${anchor}`;
}

function splitAnchor(url) {
  const index = url.indexOf('#');
  return index < 0 ? [url, ''] : [url.slice(0, index), url.slice(index)];
}

/**
 * The code of a sample without its annotation comments. The lines of a `!hide-focused` range are
 * gone, and each `!diag` annotation is a comment with the diagnostic name above the line that it
 * marks.
 */
export function renderSample(code) {
  const lines = [];
  const hidden = new Set();
  const pendingDiagnostics = [];

  for (const line of code.split('\n')) {
    const annotation = line.match(ANNOTATION_COMMENT);
    if (!annotation) {
      lines.push({text: line, diagnostics: pendingDiagnostics.splice(0)});
      continue;
    }

    const [, , name, from = '1', to = '1', pattern, flags = '', query = ''] = annotation;
    if (name === 'hide-focused') {
      for (let offset = Number(from); offset <= Number(to); offset += 1) {
        hidden.add(lines.length + offset - 1);
      }
    } else if (name === 'diag') {
      pendingDiagnostics.push({
        name: query.match(DIAGNOSTIC_NAME)?.[0] ?? query,
        pattern,
        range: new RegExp(pattern, flags.replace('g', '')),
        searchForward: flags.includes('m'),
      });
    }
  }

  const visible = lines.filter((_, index) => !hidden.has(index));
  const output = [];
  visible.forEach((line, index) => {
    for (const diagnostic of line.diagnostics) {
      const marked = diagnostic.searchForward
        ? (visible.slice(index).find((candidate) => diagnostic.range.test(candidate.text)) ?? line)
        : line;
      const match = marked.text.match(diagnostic.range);
      const indent = marked.text.match(/^\s*/)[0];
      (marked.comments ??= []).push(`${indent}// ${diagnostic.name} on '${match?.[0] ?? diagnostic.pattern}'`);
    }
  });
  for (const line of visible) {
    output.push(...(line.comments ?? []), line.text);
  }

  return trimBlankLines(output).join('\n');
}

// The Focused view trims the blank lines at both ends and merges adjacent blank lines.
function trimBlankLines(lines) {
  const result = [];
  for (const line of lines) {
    const blank = line.trim() === '';
    if (blank && (result.length === 0 || result.at(-1).trim() === '')) continue;
    result.push(line);
  }
  while (result.length > 0 && result.at(-1).trim() === '') result.pop();
  return result;
}
