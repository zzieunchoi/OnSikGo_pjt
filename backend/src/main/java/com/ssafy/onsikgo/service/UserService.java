package com.ssafy.onsikgo.service;

import com.ssafy.onsikgo.dto.LoginDto;
import com.ssafy.onsikgo.dto.OwnerDto;
import com.ssafy.onsikgo.dto.TokenDto;
import com.ssafy.onsikgo.dto.UserDto;
import com.ssafy.onsikgo.entity.LoginType;
import com.ssafy.onsikgo.entity.User;
import com.ssafy.onsikgo.repository.UserRepository;
import com.ssafy.onsikgo.security.JwtFilter;
import com.ssafy.onsikgo.security.TokenProvider;
import com.ssafy.onsikgo.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Random;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final AwsS3Service awsS3Service;
    private final JavaMailSenderImpl mailSender;
    private final StoreService storeService;

    private final RedisUtil redisUtil;
    public ResponseEntity<String> checkEmail(String email) {
        if (userRepository.findByEmail(email).orElse(null) != null) {
            log.info("이미존재하는 이메일");
            return new ResponseEntity<>("이미 존재하는 이메일입니다.", HttpStatus.NO_CONTENT);
        }
        return sendEmail(email);
    }
    public ResponseEntity<String> sendEmail(String email) {
        Random rand = new Random();
        String authNumber = String.valueOf(rand.nextInt(999999));

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage,"UTF-8");

        String setFrom = "wlgns3914@naver.com";
        String toMail = email;
        String title = "회원 가입 인증 이메일 입니다.";
        String content =
                "OnSikGo를 방문해주셔서 감사합니다." +
                        "<br><br>" +
                        "인증 번호는 " + authNumber + "입니다." +
                        "<br>" +
                        "해당 인증번호를 인증번호 확인란에 기입하여 주세요.";
        try {
            mimeMessageHelper.setFrom(setFrom);
            mimeMessageHelper.setTo(toMail);
            mimeMessageHelper.setSubject(title);
            mimeMessageHelper.setText(content,true);
            mailSender.send(mimeMessage);

        } catch (MessagingException e) {
            e.printStackTrace();
            return new ResponseEntity<>("이메일 인증에 실패했습니다. 다시 시도해주세요.", HttpStatus.NO_CONTENT);
        }
        // 5분 동안만 인증번호 저장
        redisUtil.setDataExpire(email, authNumber, 60 * 5L);

        return new ResponseEntity<>("인증번호를 전송했습니다. 메일을 확인해주세요.", HttpStatus.OK);
    }
    public ResponseEntity<String> checkAuthNumber(String email,String authNum) {
        if(!redisUtil.getData(email).equals(authNum)){
            return new ResponseEntity<>("인증 번호가 일치하지 않습니다.", HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>("이메일 인증에 성공하였습니다.", HttpStatus.OK);
    }
    public ResponseEntity<String> checkNickname(String nickname) {
        if (userRepository.findByNickname(nickname).orElse(null) != null) {
            log.info("이미존재하는 닉네임");
            return new ResponseEntity<>("이미 존재하는 닉네임입니다.", HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>("사용가능한 닉네임입니다.", HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<String> signup(UserDto userDto) {

        userDto.setPassword(passwordEncoder.encode(userDto.getPassword()));
        userDto.setImgUrl("https://onsikgo.s3.ap-northeast-2.amazonaws.com/user/pngwing.com.png");

        User user = userDto.toEntity(LoginType.ONSIKGO);

        userRepository.save(user);
        return new ResponseEntity<>("success", HttpStatus.OK);
    }


    @Transactional
    public ResponseEntity<String> signupOwner(OwnerDto ownerDto) {
        ownerDto.setPassword(passwordEncoder.encode(ownerDto.getPassword()));
        ownerDto.setImgUrl("https://onsikgo.s3.ap-northeast-2.amazonaws.com/user/pngwing.com.png");

        String storeName = ownerDto.getStoreName();
        User user = ownerDto.toUserEntity(LoginType.ONSIKGO, storeName);
        userRepository.save(user);

        storeService.firstRegister(ownerDto, user);
        return new ResponseEntity<>("success", HttpStatus.OK);
    }

    public ResponseEntity<TokenDto> login(@RequestBody LoginDto loginDto) {
        //  LoginDto의 userName,Password를 받아서 UsernamePasswordAuthenticationToken 객체를 생성한다
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginDto.getEmail(), loginDto.getPassword());

        // authenticationToken 을 이용해서 Authentication 객체를 생성하려고 authenticate메서드가 실행될때
        // CustomUserDetailsService 의 loadUserByUsername 메서드가 실행된다.
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        // 생성된 Authentication 객체를 SecurityContextHolder에 저장하고,
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // 그 인증정보를 기반으로 토큰을 생성한다
        String jwt = tokenProvider.createToken(authentication);

        HttpHeaders httpHeaders = new HttpHeaders();
        // 생성한 토큰을 Response 헤더에 넣어주고,
        httpHeaders.add(JwtFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);
        
//         TokenDto에도 넣어서 RequestBody로 리턴해준다
        return new ResponseEntity<>(new TokenDto(jwt), httpHeaders, HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<String> modify(UserDto userDto, HttpServletRequest request) {
        String token = request.getHeader("access-token");
        if (!tokenProvider.validateToken(token)) {
            return new ResponseEntity<>("유효하지 않는 토큰", HttpStatus.NO_CONTENT);
        }

        String userEmail = String.valueOf(tokenProvider.getPayload(token).get("sub"));

        User findUser = userRepository.findByEmail(userEmail).get();
        findUser.update(userDto.getNickname(), awsS3Service.uploadImge(userDto.getFile()));

        userRepository.save(findUser);
        return new ResponseEntity<>("수정완료", HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<String> delete(HttpServletRequest request) {
        String token = request.getHeader("access-token");
        if (!tokenProvider.validateToken(token)) {
            return new ResponseEntity<>("유효하지 않는 토큰", HttpStatus.NO_CONTENT);
        }

        String userEmail = String.valueOf(tokenProvider.getPayload(token).get("sub"));
        User findUser = userRepository.findByEmail(userEmail).get();

        userRepository.delete(findUser);

        if(userRepository.findByEmail(userEmail).orElse(null) != null) {
            return new ResponseEntity<>("삭제실패", HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>("삭제완료", HttpStatus.OK);
    }

    public ResponseEntity<String> pwCheck(LoginDto loginDto, HttpServletRequest request) {
        String token = request.getHeader("access-token");
        if (!tokenProvider.validateToken(token)) {
            return new ResponseEntity<>("유효하지 않는 토큰", HttpStatus.NO_CONTENT);
        }

        String userEmail = String.valueOf(tokenProvider.getPayload(token).get("sub"));
        User findUser = userRepository.findByEmail(userEmail).get();

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if(!encoder.matches(loginDto.getPassword(), findUser.getPassword())) {
            log.info("비밀번호가 틀렸습니다");
            return new ResponseEntity<>("비밀번호가 틀렸습니다", HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>("비밀번호 확인완료", HttpStatus.OK);
    }

    public ResponseEntity<String> pwChange(LoginDto loginDto, HttpServletRequest request) {
        String token = request.getHeader("access-token");
        if (!tokenProvider.validateToken(token)) {
            return new ResponseEntity<>("유효하지 않는 토큰", HttpStatus.NO_CONTENT);
        }

        String userEmail = String.valueOf(tokenProvider.getPayload(token).get("sub"));
        User findUser = userRepository.findByEmail(userEmail).get();

        findUser.changePw(passwordEncoder.encode(loginDto.getPassword()));
        userRepository.save(findUser);

        return new ResponseEntity<>("비밀번호 변경완료", HttpStatus.OK);
    }

    public ResponseEntity<UserDto> getInfo(HttpServletRequest request) {
        String token = request.getHeader("access-token");
        if (!tokenProvider.validateToken(token)) {
            return new ResponseEntity<>(new UserDto(), HttpStatus.NO_CONTENT);
        }

        String userEmail = String.valueOf(tokenProvider.getPayload(token).get("sub"));
        User findUser = userRepository.findByEmail(userEmail).get();

        UserDto userDto = findUser.toDto();

        return new ResponseEntity<>(userDto, HttpStatus.OK);
    }
}
