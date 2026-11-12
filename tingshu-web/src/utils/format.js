/**
 * 解析 ES 返回的标题（可能含 <span style="color:red"> 高亮标记）。
 * 只取文本节点，不把服务端字符串当 HTML 插入页面。
 */
export function titleParts(value) {
  const parsed = new DOMParser().parseFromString(String(value || ''), 'text/html')
  const result = []
  function visit(node, highlight = false) {
    if (node.nodeType === 3) {
      result.push({ text: node.textContent || '', highlight })
      return
    }
    if (['SCRIPT', 'STYLE', 'IFRAME', 'OBJECT'].includes(node.nodeName)) return
    for (const child of node.childNodes) {
      visit(child, highlight || ['EM', 'MARK', 'SPAN'].includes(node.nodeName))
    }
  }
  visit(parsed.body)
  return result
}

export function plainTitle(value) {
  return titleParts(value).map((part) => part.text).join('')
}
