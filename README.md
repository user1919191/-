# 项目架构
* 本项目使用Java开发,基于SpringBoot+LangChain4J框架。结合MySQL,Redis,RocketMQ,ES,RAG实现功能扩展和性能提升。
* 在本项目中实现了题目,题库的增删改查和分页查询。管理员可以增加修改删除题目题库关联并且通过AI生成题目题解。用户可以设置自己的用户名片和进行发帖操作,并且用户可以点赞和收藏帖子。用户可以签到,搜索题目,查看题目题解。本项目与LangChain4j结合,结合DashScope调用大模型,PgVector存储向量,RAG实现增强检索,实现多种模拟面试功能。
* 以下为项目架构图:
![无标题-2025-05-05-1146 3](https://github.com/user-attachments/assets/54449271-5443-437c-9a4e-ee60ed01508b)
* 以下为本项目的自纠错RAG架构图:
![无标题-2025-05-05-1146 4](https://github.com/user-attachments/assets/ce8bd46c-084c-4e98-98d3-db01804a2085)

## 运行示例:
![PixPin_2025-05-05_16-42-25](https://github.com/user-attachments/assets/259af430-130d-4485-b6d3-33239ef8c890)
![PixPin_2025-05-05_16-43-23](https://github.com/user-attachments/assets/1234c37c-c4f0-4e15-8ed8-126c65fd2684)
![PixPin_2025-05-05_16-43-58](https://github.com/user-attachments/assets/295ee031-2a9d-49bc-bd57-b96e21907da1)
![PixPin_2025-05-05_16-47-04](https://github.com/user-attachments/assets/89cd3e63-7abf-4073-9c08-b310b5bef507)
![PixPin_2025-05-09_23-05-03](https://github.com/user-attachments/assets/b5c11da7-c06b-4da9-8d24-7f0af411c81d)

# 具体实现
## 题目业务模块
1. 将题目的标签字段以String存储在数据库中,返回前端时将String转化为List<String>便于展示。
2. 使用BitMap优化用户签到功能,实现更小的存储占用.通过位运算来定位签到情况,通过牺牲签到数据的可读性换取更优的存储性能。在将签到数据返回前端时,通过位运算将已签到的日期(在某年中的DayOfYear)封装为List返回给前端,可以直接展示。
3. 数据库数据采用逻辑删除,提升性能和数据可恢复性。
4. 在题目修改时,通过Canal监听BinLog和同步ES解耦。
5. 在题目删除时,通过将Caffeine的value设置为Null,避免缓存击穿。
6. 引入DeepSeek大模型实现题目题解的自动生成,并将Status设置为待审核,等待管理员审核通过才可见。编写精简的Prompt降低Token开销。
7. 分页时,使用查询记录返回下一页首ID,将ID作为查询条件解决深分页问题。
8. 对于Caffeine缓存实现缓存预热,并且通过Spring Schedule定时检测和清理Caffeine状态,记录命中率和占用率到日志文件。当命中率低时,从HotKey同步热点数据;当占用率高时,首先让Caffeine自动清理本地缓存,再次检测占用率,如果占用率依旧高,手动通过LFU算法,依据CurrentTotal*0.2动态清理冷数据释放空间。实现缓存高可用。
9. 使用Sentinel针对分页查询和单个题目查询进行限流,防治大量数据访问导致系统不可用。编写降级和限流方案,限流返回Null,降级查询缓存数据。提高系统可用性和稳定性。

## 题库模块
1. 建立题目题库关联表,便于通过题目定位题库,使用题库检索题目。避免对于题目表,题库表的全表扫描查询数据,减少了性能损耗。
2. 基于线程池+ComputableFuture+异步实现题目题库关联的批量修改,避免业务阻塞,提高了系统性能。

## 模拟面试模块
1. 基于LangChain4J框架+DashScope实现对话记忆和临时对话存储。
2. 通过DashScope的多模态模型的图像识别,实现真实的基于简历面试和简历的针对性优化意见,提高了用户的体验感和面试的真实性。
3. 通过建立自纠错RAG(Currective-RAG),当用户请求传入时,先进行本地RAG检索增强,将生成的Context和UserPrompt传入大模型生成。得到结果后,进行词检索和逻辑检索。通过后返回结果。
4. 将原本的内存向量库优化为PgVector向量库,避免的每次启动都要将文本向量化加载进内存向量库。可以通过PgVector建立索引实现高效的查询检索。
5. 将返回的SystemPrompt进行格式纠正和规范化,去除markDowm格式中不必要的符号,返回String字符串。





