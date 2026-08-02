import MDXComponents from '@theme-original/MDXComponents';
import Code from '@site/src/components/Code';

// Code Hike replaces every fenced code block with <Code />, so it has to be in the MDX scope.
export default {
  ...MDXComponents,
  Code,
};
