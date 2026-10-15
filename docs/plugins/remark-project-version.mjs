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

export function remarkProjectVersion({libraryVersion = projectVersion, kotlin = kotlinVersion} = {}) {
  const versionTemplates = new Map([
    [projectVersionTemplate, libraryVersion],
    [kotlinVersionTemplate, kotlin],
  ]);
  return (tree) => {
    visit(tree, ['code', 'inlineCode'], (node) => {
      for (const [template, version] of versionTemplates) {
        node.value = node.value.replaceAll(template, version);
      }
    });
  };
}

export function readProjectVersion(environment = process.env) {
  let version = environment.LIBRARY_VERSION;
  if (version === undefined) {
    const properties = fs.readFileSync(propertiesFile, 'utf8');
    const versions = [...properties.matchAll(/^version=(.*)$/gm)];
    if (versions.length !== 1) {
      throw new Error(`Expected exactly one version property in ${propertiesFile}.`);
    }
    version = versions[0][1].trim();
  }
  if (version.trim() !== version || !/^\d+\.\d+\.\d+(?:[.-][0-9A-Za-z]+)*$/.test(version)) {
    throw new Error('Invalid library version. Use MAJOR.MINOR.PATCH with an optional alphanumeric suffix.');
  }
  return version;
}

function readKotlinVersion() {
  const catalog = fs.readFileSync(versionCatalogFile, 'utf8');
  const versions = [...catalog.matchAll(/^kotlin\s*=\s*"([^"]+)"\s*$/gm)].map((match) => match[1]);
  if (versions.length !== 1 || versions[0].length === 0) {
    throw new Error(`Expected exactly one non-empty Kotlin version in ${versionCatalogFile}.`);
  }
  return versions[0];
}
