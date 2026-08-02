import React, {useEffect, useState, type ReactNode} from 'react';
import styles from './KeyboardShortcut.module.css';

export type KeyboardShortcutValue = {
  label: string;
  keys: string[];
  separator: string;
};

const MAC_GENERATE_SHORTCUT: KeyboardShortcutValue = {
  label: '⌘N',
  keys: ['⌘', 'N'],
  separator: '',
};

const OTHER_GENERATE_SHORTCUT: KeyboardShortcutValue = {
  label: 'Alt+Insert',
  keys: ['Alt', 'Insert'],
  separator: '+',
};

/** Returns a hydration-safe shortcut, then selects the visitor's OS in the browser. */
export function useIdeaGenerateShortcut(): KeyboardShortcutValue {
  const [shortcut, setShortcut] = useState(OTHER_GENERATE_SHORTCUT);
  useEffect(() => {
    const platform = navigator.platform || navigator.userAgent;
    if (/Mac|iPhone|iPad|iPod/i.test(platform)) setShortcut(MAC_GENERATE_SHORTCUT);
  }, []);
  return shortcut;
}

export function KeyboardShortcut({value}: {value: KeyboardShortcutValue}): ReactNode {
  return (
    <kbd className={styles.shortcut} aria-label={value.label}>
      {value.keys.map((key, index) => (
        <React.Fragment key={key}>
          {index > 0 && value.separator && <span className={styles.separator}>{value.separator}</span>}
          <span className={styles.key}>{key}</span>
        </React.Fragment>
      ))}
    </kbd>
  );
}

export default function IdeaGenerateShortcut(): ReactNode {
  return <KeyboardShortcut value={useIdeaGenerateShortcut()} />;
}
