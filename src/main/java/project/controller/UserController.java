package project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import project.common.BaseResponse;
import project.model.vo.loginUserVO;

@Controller
@RequestMapping("/user")
public class UserController {

    @PostMapping("/register")
    public BaseResponse<Long> register() {

    }

    @RequestMapping("/login")
    public BaseResponse<loginUserVO> login() {
}
