
var Collectors = {
  init: function() {
    this.getCollectors();
    this.addCollectors();
    this.delCollectors();
    this.editCollectors();

  },
  addCollectors:function(){
    var add=$("#addCollectors");

    add.click(function(){
      var html='<input class="colName" type="text" value=""/><input class="colUrl" type="text" value=""/><p><a id="colSaveBtn" class="btn_blue" href="javascript:void(0);">确定</a></p>';
      Common.setPop("<span class='sqlIcon tipIcon'></span>添加数据收集器",html);            
    })

    $("#colSaveBtn").live("click",function(){
        var name=$(".colName").val(),
            url=$(".colUrl").val();
        $.ajax({
              url: "/sql/collectors/",
              type: 'post',
              data:{
                        "name": name,
                        "url": url,  
                        "timestamp": Common.getTimes()
              },
              dataType: 'json',
              error: function(){},
              success: function(data){

              },
              statusCode:{
                201:function(){Boxy.alert("添加成功",function(){Query.delBoxy();})},
                401:function(){},
                409:function(){}
              }

          });      
    })
    
  
  },
  getCollectors:function(){
        $.ajax({
              url: "/sql/collectors/",
              type: 'get',
              data:{
                        "timestamp": Common.getTimes()
              },
              dataType: 'json',
              error: function(){},
              success: function(data){

              },
              statusCode:{
                401:function(){alert("暂时没有结果")},
                200:function(data){
                  if(!data.length){Boxy.alert("暂时没有常用查询");return;}
                  var titles=[],v=[];

                  for (var j=0,l=data.length;j<l;j++){
                      titles[j]=[];
                      v[j]=[];
                      if(data[j].status=="no-sync"){
                        data[j]["recent-sync"]="--";
                        data[j]["synced-data"]="--";
                      }                      
                      for (var i in data[j]){                 
                        titles[j].push(i);
                        v[j].push(data[j][i]);
                      }

                      var del=Query.getTablesOp("del",data[j].id),edit=Query.getTablesOp("edit",data[j].id+"__"+data[j].name+"__"+data[j].url);
                      v[j].push(del+" "+edit);              
                  }

                  titles[0].push("操作");
                  Common.setGrid(titles[0],v,"<span class='sqlIcon tipIcon'></span>常用查询");
                }
              }

          });
  },
  delCollectors:function(){

    var del=$(".colTable .tablesDel");

    del.live("click",function(){
        var id=$(this).attr("rel");
        $.ajax({
              url: "/sql/collectors/"+id,
              type: 'delete',
              data:{    
                        "timestamp": Common.getTimes()
              },
              dataType: 'json',
              error: function(){},
              success: function(data){

              },
              statusCode:{
                404:function(){},
                401:function(){},
                200:function(){
                  Collectors.getCollectors();
                }
              }

          });      
    })

  },
  editCollectors:function(){
    var edit=$(".colTable .tablesEdit");

    edit.live("click",function(){
        var rel=$(this).attr("rel"),
            data=rel.split("__");
        
        id=data[0],name=data[1],url=data[2];

      var html='<input class="colName" type="text" value="'+name+'"/><input class="colUrl" type="text" value="'+url+'"/><p><a id="colEditBtn" class="btn_blue" href="javascript:void(0);">确定</a></p>';
      Common.setPop("<span class='sqlIcon tipIcon'></span>添加数据收集器",html);

       $("#colEditBtn").live("click",function(){
          $.ajax({
                url: "/sql/collectors/"+id,
                type: 'put',
                data:{
                          "name":name,
                          "url":url,                  
                          "timestamp": Common.getTimes()
                },
                dataType: 'json',
                error: function(){},
                success: function(data){

                },
                statusCode:{
                  404:function(){},
                  401:function(){},
                  403:function(){},
                  409:function(){},                                
                  200:function(data){

                  }
                }

            });

       }) 
      
    })

    
  }
}
