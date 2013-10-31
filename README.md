#   mock query server

## 使用说明

*   安装[JDK7](http://java.oracle.com)
*   安装[Leiningen](https://github.com/technomancy/leiningen)
*   替换或添加自己的html/css/js/...到publics目录下
*   执行"lein run -p 12345"
*   打开浏览器访问localhost:12345/sql/以访问查询页面
*   打开浏览器访问localhost:12345/sql/admin.html以访问收集器管理页面
*   可以登录的email: a@b.c，密码123

##  publics目录下文件的作用

*   index.html：用户登录的页面
*   query.html：用户查询的页面
*   query_server.js：从后端获取数据并很土的展示的逻辑
*   core.cljs：用以编译成query_server.js的ClojureScript的源文件

##  更新

*   收集器新增"abandoned"状态
*   删除收集器时必须给出理由
*   编辑收集器将name和url带在params里，而不是body里。jquery-1.7不能正确处理请求的body。
*   查询结果带"count"条目，表示查询结果的条数。后端传给前端的查询结果可能只是全部结果的一部分。
*   查询相关返回值里的"submit-time"条目改名为"submit_time"。

##  License

Copyright © 2013 Flying-Sand
