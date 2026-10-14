#!/bin/bash

# Appends a <url> entry for every API docs HTML page to a sitemap.xml file.
#
# Usage, from the repository root:
#   ./scripts/update_sitemap.sh <sitemap_file> <api_docs_dir>
# Example:
#   ./scripts/update_sitemap.sh build/dokka/sitemap.xml build/dokka/api

set -euo pipefail

if [ -z "${1:-}" ] || [ -z "${2:-}" ]; then
  echo "Usage: $0 <sitemap_file> <api_docs_dir>"
  echo "Example: $0 sitemap.xml docs/pages/api"
  exit 1
fi

sitemap_file=$1
api_docs_dir=$2

if [ ! -f "$sitemap_file" ]; then
  echo "Error: Sitemap file '$sitemap_file' doesn't exist."
  exit 1
fi

if [ ! -d "$api_docs_dir" ]; then
  echo "Error: API docs directory '$api_docs_dir' doesn't exist."
  exit 1
fi

current_date=$(date +%Y-%m-%d)

temp_file=$(mktemp)

# The first line without its closing urlset tag. Docusaurus writes the sitemap on one line, so
# the line holds the XML header and every existing entry.
head -n 1 "$sitemap_file" | sed -En 's/<\/urlset>//p' > "$temp_file"

find "$api_docs_dir" -type f -name "*.html" | while read -r html_file; do
  url_path="${html_file/#$api_docs_dir/api}"

  url="https://mr3zee.github.io/Library-API-Watchdog/$url_path"

  cat >> "$temp_file" << EOF
    <url>
        <loc>$url</loc>
        <lastmod>$current_date</lastmod>
        <changefreq>monthly</changefreq>
        <priority>0.15</priority>
    </url>
EOF
done

echo "</urlset>" >> "$temp_file"

mv "$temp_file" "$sitemap_file"

echo "Sitemap updated successfully with entries from $api_docs_dir"
