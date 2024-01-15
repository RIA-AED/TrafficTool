# TrafficTool

Velocity 玩家流量整型和统计工具。

## 功能

* 玩家粒度的流量整形（限速）
* 服务器粒度的流量整型（进程限速）
* 查看实时流量
* 使用命令动态修改整型参数（仅本次会话有效）

## 命令

所有命令的实际参数以示例为准，如果输入后提示参数错误（不足），请在后面随意追加几个参数，将其补满 2 个参数。

* /traffic view global - 浏览Velocity上的实时流量数据
* /traffic view global <player> - 浏览指定玩家的实时流量数据
* /traffic upload - 手动上传当前实时流量数据到数据库用作分析
* /traffic config global <连接最大写速度> <连接最大读速度> - 设置Velocity网络的流量整形（可以理解为进程限速）
* /traffic config player <连接最大写速度> <连接最大读速度> - 设置指定玩家的本次连接的流量整形
* /traffic me - 查看自己的连接参数

## 示例返回


