package project.manager;

import com.alibaba.dashscope.aigc.generation.Generation;

import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class RagManager {

    @Resource
    private GenerationParam generationParam;

    private final ChatLanguageModel dashscopeModel;
    private final ContentRetriever contentRetriever;

    public RagManager(ChatLanguageModel dashscopeModel,
                             ContentRetriever contentRetriever) {
        this.dashscopeModel = dashscopeModel;
        this.contentRetriever = contentRetriever;
    }

    private static final String continuePlusPrompt = "您是一位java和中间件的专家,请根据我提供的回答,"+
            "对于回答中的编程知识从(重点,难点,面试易考点)进行补充,对于回答的评价和下一个问题不做任何修改!";
    /**
     * 混合自检查式检索
     * @param question
     * @return
     */
    public String searchQuestionWithContext(String question,boolean init) {
        // 1.检索相关内容
        List<Content> contents = contentRetriever.retrieve(Query.from(question));

        // 2.构建增强提示
        String context = contents.stream()
                .map(c -> c.textSegment().text())
                .collect(Collectors.joining("\n---\n"));
        String searchByAI = null;
        if(init){
           String initPrompt = String.format("""
             根据知识库的提示生成一个Java和中间件领域的问题
             %s
             要求: %s
            """, context, question);
           searchByAI = searchWithAI(initPrompt);
           return searchByAI;
       }
       else{
           String prompt = String.format("""
             知识库内容:
             %s.
             以下使用知识库对于'面试者回答'纠正或补充,并且基于知识库生成问题:
             %s
             注意：
             - 如果无法进行补充，明确说明“我不知道”
             - 避免生成重复问题
             - 格式为 '问题:(生成的的问题)'
            """, context, question);

           // 3.调用模型生成初始回答
          searchByAI = searchWithAI(prompt);

           //4.自检校验是否合格
           boolean validate = validate(question, searchByAI);
           if(!validate){
               searchByAI = searchWithAI("请重新回答" + searchByAI);
               //4.2如果多次生成不合格,提交人工,返回无法生成
               if(!validate(question, searchByAI)){
                   //Todo 可存到数据库持久化
                   log.warn("AI生成失败,问题:{},答案:{}",question,searchByAI);
                   return searchByAI;
               }
           }
       }
       return searchByAI;
    }

    /**
     * 检索AI
     * @param updateQuestion
     * @return
     */
    public String searchWithAI(String updateQuestion){
        //1.AI生成
        Generation gen = new Generation();
        Message build = Message.builder().role(Role.ASSISTANT.getValue()).content(updateQuestion).build();
        generationParam.setMessages(List.of(build));
        GenerationResult call = null;
        try{
            call = gen.call(generationParam);
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        } catch (InputRequiredException e) {
            throw new RuntimeException(e);
        }
        return call.getOutput().getChoices().get(0).getMessage().getContent().toString();
    }

    /**
     * 混合检验策略
     * @param answer
     * @return
     */
    public boolean validate(String question,String answer){
        //1.首先规则驱动检验
        if(answer.contains("我不知道") || answer.contains("可能") || answer.contains("但是") || answer.contains("不清楚")){
            return false;
        }
        //2.其次AI校验
//        String prompt = String.format("验证回答是否合理：%s -> %s,如果合理输出合理,不合理输出不合理,不做其余多余输出", question, answer);
//        String checkedAnswer = searchWithAI(prompt);
//        return checkedAnswer.contains("合理");
        return true;
    }
}
