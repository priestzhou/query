#   mock query server

## 使用说明

*   安装[JDK7](http://java.oracle.com)
*   安装[Leiningen](https://github.com/technomancy/leiningen)
*   替换或添加自己的html/css/js/...到publics目录下
*   执行"lein run"

##  publics目录下文件的作用

*   index.html：用户登录的页面
*   query.html：用户查询的页面
*   query_server.js：从后端获取数据并很土的展示的逻辑
*   core.cljs：用以编译成query_server.js的ClojureScript的源文件

##  已知问题

1.  没有实现验证user_id失败返回401的逻辑

##  License

Copyright © 2013 Flying-Sand
