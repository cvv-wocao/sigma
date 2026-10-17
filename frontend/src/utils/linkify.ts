/**
 * 把纯文本里的 http(s) 链接转成可点击的 <a>，用于渲染 Agent 回复。
 *
 * <p>为什么需要它：Agent 回答里会直接给出仓库地址（https://github.com/owner/repo），
 * 之前按纯文本渲染，用户只能自己复制粘贴——「搜出来的项目点不开」有一半是这里的问题。</p>
 *
 * <p>安全说明：先对文本做 HTML 转义再拼 <a>，模型输出里的 &lt;script&gt; 之类不会被执行；
 * 链接只允许 http/https，javascript: 之类的伪协议不会被匹配到。</p>
 */

/** 匹配 http(s) 链接；排除空白、引号、尖括号与中文标点/右括号，避免把句末的「。」吞进链接 */
const URL_PATTERN = /(https?:\/\/[^\s<>"'）)】〕」》,。！？；：、]+)/g

const HTML_ESCAPES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => HTML_ESCAPES[char] ?? char)
}

export function linkify(text: string): string {
  if (!text) {
    return ''
  }
  return text
    .split(URL_PATTERN)
    .map((part, index) => {
      // split 带捕获组时，链接片段固定落在奇数下标上
      if (index % 2 === 0) {
        return escapeHtml(part)
      }
      const trimmed = part.replace(/[.,;:]+$/, '')
      const tail = part.slice(trimmed.length)
      const anchor = `<a href="${escapeHtml(trimmed)}" target="_blank" rel="noreferrer">${escapeHtml(trimmed)}</a>`
      return `${anchor}${escapeHtml(tail)}`
    })
    .join('')
}