
本项目基于Java开发,结合Redis,MQ,ES实现功能扩展和性能提升。在项目中实现了题目,题库的增删改查和分页查询。管理员可以增加修改删除题目题库关联。并且通过逻辑删除对数据留底。用户可以设置自己的用户名片和进行发帖操作,并且用户可以点赞和收藏帖子。以下为项目架构图:
![无标题-2025-05-05-1146](https://github.com/user-attachments/assets/b47c368e-10b6-4de5-bcb5-5004454af88e)
运行示例:
![PixPin_2025-05-05_16-42-25](https://github.com/user-attachments/assets/259af430-130d-4485-b6d3-33239ef8c890)
![PixPin_2025-05-05_16-43-23](https://github.com/user-attachments/assets/1234c37c-c4f0-4e15-8ed8-126c65fd2684)
![PixPin_2025-05-05_16-43-58](https://github.com/user-attachments/assets/295ee031-2a9d-49bc-bd57-b96e21907da1)
![PixPin_2025-05-05_16-47-04](https://github.com/user-attachments/assets/89cd3e63-7abf-4073-9c08-b310b5bef507)

具体实现:
    题目业务:
1.将题目的标签字段以String存储在数据库中,返回前端时将String转化为List<String>便于展示。
2.使用BitMap优化用户签到功能,实现更小的存储占用.通过位运算来定位签到情况,通过牺牲签到数据的可读性换取更优的存储性能。在将签到数据返回前端时,通过位运算将已签到的日期(在某年中的DayOfYear)封装为List返回给前端,可以直接展示。
3.数据库数据采用逻辑删除,提升性能和数据可恢复性。
4.在题目添加时,通过异步将数据同步ES解耦。
5.在题目删除时,通过将Caffeine的value设置为Null,避免缓存击穿。
6.通过自定义线程池+CompleteFuture+批处理提升题目批量新增性能。
7.引入DeepSeek大模型实现题目题解的自动生成,并将Status设置为待审核,等待管理员审核通过才可见。编写精简的Prompt降低Token开销。
8.使用记录返回ID,将ID作为查询条件解决深分页问题。
9.对于Caffeine缓存实现缓存预热,并且通过Spring Schedule定时检测和清理Caffeine状态,记录命中率和占用率到日志文件。当命中率低时,从HotKey同步热点数据;当占用率高时,首先让Caffeine自动清理本地缓存,再次检测占用率,如果占用率依旧高,手动通过LFU算法,依据CurrentTotal*0.2动态清理冷数据释放空间。实现缓存高可用。