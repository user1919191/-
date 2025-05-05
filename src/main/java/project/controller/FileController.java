package project.controller;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import project.common.BaseResponse;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.constant.FileConstant;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.manager.CosManager;
import project.model.dto.file.UploadFileRequest;
import project.model.entity.User;
import project.model.enums.FileUploadBizEnum;
import project.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 文件接口
 */

@Controller
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    @Resource
    private CosManager cosManager;

    public BaseResponse<String> uploadFile(@RequestPart("file")MultipartFile multipartFile,
                                           UploadFileRequest uploadFileRequest, HttpServletRequest httpServletRequest)
    {
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum enumByValue = FileUploadBizEnum.getEnumByValue(biz);
        ThrowUtil.throwIf(enumByValue == null, ErrorCode.PARAMS_ERROR);
        validFile(multipartFile,enumByValue);
        User loggedUser = userService.getLoginUser(httpServletRequest);
        //文件目录
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String fileName = uuid + "-" + multipartFile.getName();
        String filepath = StrUtil.format("/{}/{}/{}",enumByValue.getValue(),loggedUser.getId(),fileName);
        File file = null;
        try{
            file = File.createTempFile(filepath,null);
            multipartFile.transferTo(file);
            cosManager.putObjectRequest(filepath,file);
            //返回可访问地址
            return ResultUtil.success(FileConstant.Cos_Address + filepath);
        }catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"文件上传失败");
        }finally {
            if(file != null){
                boolean delete = file.delete();
                if(!delete)
                {
                    log.error("file delete error = {}",filepath);
                }
            }

        }
    }

    /**
     * 校验文件
     * @param multipartFile
     * @param fileUploadBizEnum
     */
    public void validFile(MultipartFile multipartFile,FileUploadBizEnum fileUploadBizEnum)
    {
        long size = multipartFile.getSize();
        //文件后缀
        String suffix = FileNameUtil.getSuffix(multipartFile.getOriginalFilename());
        final long ONE_PICTURE = 5 * 1024 * 1024L;
        if(FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum))
        {
            if(size > ONE_PICTURE)
            {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件大小不能超过5M");
            }
            if(!Arrays.asList("jpeg","jpg","png","webp").contains(suffix))
            {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件格式不正确");
            }
        }
    }
}
