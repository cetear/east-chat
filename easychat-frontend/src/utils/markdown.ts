import { Marked } from 'marked'
const escapeHtml = (value: string) => value.replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]!)
const markdown = new Marked({
  breaks: true,
  renderer: {
    html({ text }) { return escapeHtml(text) },
    link({ href, tokens }) {
      const label = this.parser.parseInline(tokens)
      try {
        const url = new URL(href)
        if (['https:', 'http:', 'mailto:'].includes(url.protocol)) return `<a href="${escapeHtml(url.href)}" target="_blank" rel="noopener noreferrer">${label}</a>`
      } catch { /* unsupported URL */ }
      return label
    },
    image({ text }) { return escapeHtml(text) },
  },
})
export function renderMarkdown(text: string): string {
  return markdown.parse(text, { async: false })
}
