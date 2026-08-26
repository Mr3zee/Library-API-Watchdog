import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';
import {variables} from './variables.mjs';

// Keep the navigation order explicit so check pages stay alphabetical within their groups.
const sidebars: SidebarsConfig = {
  docs: [
    'overview',
    'configuration',
    'existing-libs',
    'exemptions',
    'abi-validation-suggestion',
    {
      type: 'category',
      label: 'Checks',
      collapsed: false,
      items: [
        'checks/boolean-parameter-public-api',
        'checks/data-class-public-api',
        'checks/exhaustive-public-api',
        'checks/function-type-alias-public-api',
        'checks/inconsistent-parameter-order-in-overloads',
        'checks/inline-function-with-logic',
        'checks/mutable-collection-public-api',
        'checks/nullable-boolean-public-api',
        'checks/open-api-without-subclass-opt-in',
        'checks/pair-or-triple-public-api',
        'checks/required-parameter-after-optional',
        'checks/stateful-class-without-equals-hashcode-to-string',
        'checks/subclass-opt-in-without-markers',
        'checks/undocumented-public-api',
        {
          type: 'category',
          label: 'Java interop',
          link: {type: 'doc', id: 'checks/java-interop/java-interop'},
          items: [
            'checks/java-interop/companion-api-without-jvm-static',
            'checks/java-interop/companion-property-without-static-access',
            'checks/java-interop/default-parameters-without-jvm-overloads',
            'checks/java-interop/kotlin-only-api-without-jvm-synthetic',
            'checks/java-interop/mangled-jvm-name-public-api',
            'checks/java-interop/top-level-api-without-jvm-name',
          ],
        },
        {
          type: 'category',
          label: 'Special checks',
          items: [
            'checks/special/dsl-marker-noop-type-position',
            'checks/special/dsl-marker-noop-target',
            'checks/special/dsl-marker-without-explicit-targets',
            'checks/special/exemption-without-explanation',
            'checks/special/public-type-from-non-transitive-dependency',
            'checks/special/public-type-with-internal-api',
          ],
        },
      ],
    },
    {
      type: 'link',
      label: 'API Reference',
      href: `${variables.host}/Library-API-Watchdog/api/`,
    },
  ],
};

export default sidebars;
