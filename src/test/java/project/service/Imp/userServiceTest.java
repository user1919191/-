package project.service.Imp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import project.service.UserService;

import java.util.List;

@SpringBootTest
public class userServiceTest {
    @Autowired
    private UserService userService;

    @Test
    public void test() {
        List<Integer> userSignInYear = userService.getUserSignInYear(1, 2024);
        System.out.println(userSignInYear);
    }
}
