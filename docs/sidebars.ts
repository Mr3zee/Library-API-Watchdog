import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';
import {variables} from './variables.mjs';

// Keep the navigation order explicit so related checks stay grouped together.
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
        {
          type: 'category',
          label: 'Java interop',
          link: {type: 'doc', id: 'checks/java-interop/java-interop'},
          items: [
            'checks/java-interop/mangled-jvm-name-public-api',
            'checks/java-interop/kotlin-only-api-without-jvm-synthetic',
            'checks/java-interop/companion-api-without-jvm-static',
            'checks/java-interop/companion-constant-without-jvm-field',
            'checks/java-interop/top-level-api-without-jvm-name',
            'checks/java-interop/default-parameters-without-jvm-overloads',
          ],
        },
        {
          type: 'category',
          label: 'Special checks',
          items: [
            'checks/special/exemption-without-explanation',
            'checks/special/dsl-marker-noop-target',
            'checks/special/dsl-marker-without-explicit-targets',
            'checks/special/dsl-marker-noop-type-position',
            'checks/special/public-type-with-internal-api',
            'checks/special/public-type-from-non-transitive-dependency',
          ],
        },
        'checks/open-api-without-subclass-opt-in',
        'checks/subclass-opt-in-without-markers',
        'checks/exhaustive-public-api',
        'checks/undocumented-public-api',
        'checks/function-type-alias-public-api',
        'checks/data-class-public-api',
        'checks/stateful-class-without-equals-hashcode-to-string',
        'checks/mutable-collection-public-api',
        'checks/pair-or-triple-public-api',
        'checks/boolean-parameter-public-api',
        'checks/nullable-boolean-public-api',
        'checks/required-parameter-after-optional',
        'checks/inconsistent-parameter-order-in-overloads',
        'checks/inline-function-with-logic',
      ],
    },
    {
      type: 'link',
      label: 'API Reference',
      href: `${variables.host}/libs-api-watchdog/api/`,
    },
  ],
};

export default sidebars;
