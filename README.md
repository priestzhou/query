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

*   历史查询、保存查询的返回内容从map到array，更适合js处理。
*   统一几种请求的错误码与错误信息。
*   完成登录验证的逻辑。
*   完成收集器管理页面的逻辑。
*   GET meta/返回的内容将columns改为children以方便前端处理。

##  License

Copyright © 2013 Flying-Sand
