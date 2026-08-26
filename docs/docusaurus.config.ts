import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';
import {remarkCodeHike, type CodeHikeConfig} from 'codehike/mdx';
import {remarkCodeSamples} from './plugins/remark-code-samples.mjs';
import {remarkProjectVersion} from './plugins/remark-project-version.mjs';
import {variables} from './variables.mjs';

// "github-from-css" resolves every colour to a --ch-* CSS variable (see src/css/custom.css), so
// one build-time highlighting pass serves both the light and the dark theme.
const codeHike: CodeHikeConfig = {
  components: {code: 'Code'},
  syntaxHighlighting: {theme: 'github-from-css'},
};

const config: Config = {
  title: variables.product,
  tagline: 'Warns library authors about public API that is hard to evolve',
  favicon: 'img/logo.svg',

  url: variables.host,
  baseUrl: '/Library-API-Watchdog/',
  organizationName: 'Mr3zee',
  projectName: 'Library API Watchdog',
  trailingSlash: false,

  onBrokenLinks: 'throw',
  onBrokenAnchors: 'throw',

  presets: [
    [
      'classic',
      {
        docs: {
          path: 'docs',
          routeBasePath: '/',
          sidebarPath: './sidebars.ts',
          editUrl: `${variables['repo-tree-path'].replace('/tree/', '/edit/')}/docs/`,
          // These have to run before the Docusaurus plugins turn code blocks into <Code> elements.
          beforeDefaultRemarkPlugins: [
            remarkProjectVersion,
            remarkCodeSamples,
            [remarkCodeHike, codeHike],
          ],
        },
        blog: false,
        pages: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/logo.svg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: variables.product,
      logo: {
        alt: `${variables.product} logo`,
        src: 'img/logo.svg',
      },
      items: [
        {
          href: `${variables.host}/Library-API-Watchdog/api/`,
          label: 'API Reference',
          position: 'right',
        },
        {
          href: variables['repo-root-path'],
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Get started', to: '/'},
            {label: 'Configuration', to: '/configuration'},
            {label: 'Exemptions', to: '/exemptions'},
          ],
        },
        {
          title: 'More',
          items: [
            {label: 'API Reference', href: `${variables.host}/Library-API-Watchdog/api/`},
            {label: 'GitHub', href: variables['repo-root-path']},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} JetBrains s.r.o.`,
    },
    prism: {
      // Code Hike renders every fenced block, so Prism only ever sees the theme defaults.
      theme: require('prism-react-renderer').themes.github,
      darkTheme: require('prism-react-renderer').themes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
