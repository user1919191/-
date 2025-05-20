package project.service;
import com.baomidou.mybatisplus.extension.service.IService;
import project.model.dto.DummyInterview.interViewMutiRequest;
import project.model.dto.DummyInterview.interViewRequest;
import project.model.entity.InterView;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */


/**
 * 模拟面试服务
 */
public interface DummyInterViewService extends IService<InterView> {

    /**
     * 模拟面试
     * @return
     */
    String interview(interViewRequest interviewrequest, Long userid);

    /**
     * RAG模拟面试
     * @param interviewrequest
     * @param userid
     * @return
     */
    String interviewByRAG(interViewRequest interviewrequest, Long userid);

    /**
     * 基于简历模拟面试
     * @param interviewrequest
     * @param userid
     * @return
     */
    String interviewByMuti(interViewMutiRequest interviewrequest, Long userid);

    /**
     * 简历优化
     * @param interviewrequest
     * @param userid
     * @return
     */
    String interviewUpdateByPNG(interViewMutiRequest interviewrequest, Long userid);
}
