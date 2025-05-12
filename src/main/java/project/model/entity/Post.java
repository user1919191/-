package project.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 帖子
 */
@TableName(value = "post")
@Data
public class Post implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表 json
     */
    private String tags;

    /**
     * 点赞数
     */
    private Integer thumbNum;

    /**
     * 收藏数
     */
    private Integer favourNum;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public boolean IncreaseThumb(){
        int current = this.thumbNum;
        this.thumbNum++;
        if(current + 1 == thumbNum){
            return true;
        }
        return false;
    }

    public boolean DecreaseThumb(){
        int current = this.thumbNum;
        this.thumbNum--;
        if(current - 1 == thumbNum){
            return true;
        }
        return false;
    }

    public boolean IncreaseFavour(){
        int current = this.favourNum;
        this.favourNum++;
        if(current + 1 == favourNum){
            return true;
        }
        return false;
    }

    public boolean DecreaseFavour(){
        int current = this.favourNum;
        this.favourNum--;
        if(current - 1 == favourNum){
            return true;
        }
        return false;
    }
}