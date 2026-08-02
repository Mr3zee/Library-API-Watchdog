// The pages were written for Writerside and moved over unchanged, so three of its conventions
// have to be translated for Docusaurus:
//
//   %variable%          - a substitution from variables.mjs, in prose, links, and code samples.
//   ## Title {id="x"}   - an explicit heading id, spelled {#x} in Docusaurus.
//   [](some-page.md)    - a link by bare file name, whatever folder the target lives in, with the
//                         link text taken from the target's own title.
//
// The first two are text rewrites, done in the `markdown.preprocessor` hook rather than in a
// remark plugin: MDX reads `{...}` as a JavaScript expression, so `{id="x"}` has to be gone
// before the file is parsed. The third one needs the titles of other pages, so it is a remark
// plugin over the parsed links.
//
// See ../authoring.md for the authoring rules these implement.
import fs from 'node:fs';
import path from 'node:path';
import GithubSlugger from 'github-slugger';
import {visit} from 'unist-util-visit';
import {variables} from '../variables.mjs';

const VARIABLE = /%([a-z0-9-]+)%/g;
const HEADING = /^(#{1,6} .*)$/gm;
const HEADING_ID = /\s*\{id="([^"]+)"}\s*$/;

/**
 * Rewrites the two textual Writerside conventions. Wire it up as `markdown.preprocessor`.
 *
 * @param {{fileContent: string, filePath: string}} file
 * @returns {string}
 */
export function preprocessWriterside({fileContent, filePath}) {
  return substitute(fileContent, filePath).replaceAll(HEADING, (heading) => {
    const match = heading.match(HEADING_ID);
    // The backslash is what MDX needs to read the brace as text; Docusaurus then picks the id up
    // from the heading text with its own {#id} parser.
    return match ? `${heading.slice(0, match.index).trimEnd()} \\{#${match[1]}}` : heading;
  });
}

/**
 * Substitutes the variables a second time, for the page metadata: Docusaurus derives the title
 * and the description from the content this hook returns, not from the preprocessed one. Wire it
 * up as `markdown.parseFrontMatter`.
 */
export async function parseWritersideFrontMatter({filePath, fileContent, defaultParseFrontMatter}) {
  const parsed = await defaultParseFrontMatter({filePath, fileContent});
  return {...parsed, content: substitute(parsed.content, filePath)};
}

/** Replaces every `%name%` with its value, failing loudly on an unknown variable. */
function substitute(text, filePath) {
  return text.replaceAll(VARIABLE, (match, name) => {
    if (!(name in variables)) {
      throw new Error(`Unknown variable ${match} in ${filePath}. Declare it in docs/variables.mjs.`);
    }
    return variables[name];
  });
}

/**
 * Resolves the flat `[](some-page.md#anchor)` link style: rewrites the target to a path relative
 * to the linking page, and fills an empty link text with the title of the target page, or of the
 * target section when the link points at an anchor.
 *
 * Must run before the Docusaurus link resolution, which only understands relative paths.
 */
export function remarkFlatLinks({docsDirectory}) {
  const pages = indexPages(docsDirectory);
  return (tree, file) => {
    visit(tree, 'link', (node) => {
      const [target, anchor] = node.url.split('#');
      // The flat style is a bare file name; anything with a slash or a scheme is a plain link
      // that Docusaurus resolves on its own.
      if (!/^[^/:]+\.mdx?$/.test(target)) return;
      const page = pages.get(target);
      if (!page) {
        throw new Error(`Unknown link target ${node.url} in ${file.path}.`);
      }
      const relative = path.relative(path.dirname(file.path), page.path);
      node.url = (relative.startsWith('.') ? relative : `./${relative}`) + (anchor ? `#${anchor}` : '');
      if (node.children.length > 0) return;
      const text = anchor ? page.anchors.get(anchor) : page.title;
      if (!text) {
        throw new Error(`No heading ${node.url} to take the link text from, in ${file.path}.`);
      }
      node.children = [{type: 'text', value: text}];
    });
  };
}

/** Indexes every docs page by bare file name, with the titles a link can be filled from. */
function indexPages(docsDirectory) {
  const pages = new Map();
  const walk = (directory) => {
    for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
      const entryPath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(entryPath);
      } else if (entry.name.endsWith('.md') || entry.name.endsWith('.mdx')) {
        pages.set(entry.name, readPage(entryPath));
      }
    }
  };
  walk(docsDirectory);
  return pages;
}

const TITLE = /^#\s+(.+?)\s*$/m;
const SUBTITLE = /^#{2,6}\s+(.+?)\s*(?:\{id="([^"]+)"})?\s*$/gm;
const INLINE_MARKUP = /`([^`]*)`|\*\*([^*]*)\*\*|\*([^*]*)\*|\[([^\]]*)]\([^)]*\)/g;

function readPage(pagePath) {
  const source = substitute(fs.readFileSync(pagePath, 'utf8'), pagePath);
  const title = plainText(source.match(TITLE)?.[1] ?? path.basename(pagePath));
  // One slugger per page, so repeated headings get the -1, -2 suffixes Docusaurus gives them too.
  const slugger = new GithubSlugger();
  const anchors = new Map();
  for (const [, heading, explicitId] of source.matchAll(SUBTITLE)) {
    const text = plainText(heading);
    anchors.set(explicitId ?? slugger.slug(text), text);
  }
  return {path: pagePath, title, anchors};
}

/** Strips the inline markup a title may carry, leaving the plain text a link should show. */
function plainText(text) {
  return text.replaceAll(INLINE_MARKUP, (_, code, bold, italic, link) => code ?? bold ?? italic ?? link);
}
