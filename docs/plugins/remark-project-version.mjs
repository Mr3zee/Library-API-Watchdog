import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {visit} from 'unist-util-visit';

const here = path.dirname(fileURLToPath(import.meta.url));
const propertiesFile = path.resolve(here, '../../gradle.properties');
const versionCatalogFile = path.resolve(here, '../../gradle/libs.versions.toml');

export const projectVersionTemplate = '{{libraryApiWatchdogVersion}}';
export const projectVersion = readProjectVersion();
export const kotlinVersionTemplate = '{{kotlinVersion}}';
export const kotlinVersion = readKotlinVersion();

const versionTemplates = new Map([
  [projectVersionTemplate, projectVersion],
  [kotlinVersionTemplate, kotlinVersion],
]);

/** Replaces version templates in code before Code Hike renders it. */
export function remarkProjectVersion() {
  return (tree) => {
    visit(tree, ['code', 'inlineCode'], (node) => {
      for (const [template, version] of versionTemplates) {
        node.value = node.value.replaceAll(template, version);
      }
    });
  };
}

function readProjectVersion() {
  const properties = fs.readFileSync(propertiesFile, 'utf8');
  const versions = [...properties.matchAll(/^version=(.+)$/gm)].map((match) => match[1].trim());
  if (versions.length !== 1 || versions[0].length === 0) {
    throw new Error(`Expected exactly one non-empty version property in ${propertiesFile}.`);
  }
  return versions[0];
}

function readKotlinVersion() {
  const catalog = fs.readFileSync(versionCatalogFile, 'utf8');
  const versions = [...catalog.matchAll(/^kotlin\s*=\s*"([^"]+)"\s*$/gm)].map((match) => match[1]);
  if (versions.length !== 1 || versions[0].length === 0) {
    throw new Error(`Expected exactly one non-empty Kotlin version in ${versionCatalogFile}.`);
  }
  return versions[0];
}
