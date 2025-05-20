package project.model.dto.DummyInterview;

import lombok.Data;

@Data
public class interViewMutiRequest {

    /**
     * 模拟面试序列号(由前端分配,判断是不是同一场模拟面试)
     */
    private String interviewId;

    /**
     * 面试者回答
     */
    private String answer;

    /**
     * 面试者简历图片
     */
    private String resumeImage;
}
