// 全局配置：与小程序 config/confjg.js 对齐
// BASE_URL 指向虚拟机 Nginx（80），由 Nginx 反代到 Docker 网关 8500
// 构建时可用 VITE_API_BASE_URL 覆盖（见 tingshu-web/.env），不注入则回落到本地开发地址
export const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://192.168.13.100'
