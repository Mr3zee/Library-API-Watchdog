import MDXComponents from '@theme-original/MDXComponents';
import Code from '@site/src/components/Code';
import IdeaGenerateShortcut from '@site/src/components/KeyboardShortcut';
import {useCallback, useEffect, useRef, useState, type ComponentProps} from 'react';

function Table(props: ComponentProps<'table'>) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [overflow, setOverflow] = useState({left: false, right: false});

  const updateOverflow = useCallback(() => {
    const element = scrollRef.current;
    if (!element) return;

    const next = {
      left: element.scrollLeft > 1,
      right: element.scrollLeft + element.clientWidth < element.scrollWidth - 1,
    };
    setOverflow(current =>
      current.left === next.left && current.right === next.right ? current : next,
    );
  }, []);

  useEffect(() => {
    const element = scrollRef.current;
    if (!element) return;

    updateOverflow();
    const observer = new ResizeObserver(updateOverflow);
    observer.observe(element);
    observer.observe(element.firstElementChild!);
    return () => observer.disconnect();
  }, [updateOverflow]);

  const className = [
    'table-wrapper',
    overflow.left && 'table-wrapper--overflow-left',
    overflow.right && 'table-wrapper--overflow-right',
  ].filter(Boolean).join(' ');

  return (
    <div className={className}>
      <div
        className="table-scroll"
        onScroll={updateOverflow}
        ref={scrollRef}
        tabIndex={overflow.left || overflow.right ? 0 : undefined}
      >
        <table {...props} />
      </div>
    </div>
  );
}

// Code Hike replaces every fenced code block with <Code />, so it has to be in the MDX scope.
export default {
  ...MDXComponents,
  table: Table,
  Code,
  IdeaGenerateShortcut,
};
