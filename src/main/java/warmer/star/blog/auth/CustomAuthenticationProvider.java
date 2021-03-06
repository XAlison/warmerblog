package warmer.star.blog.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.alibaba.fastjson.JSON;

import warmer.star.blog.model.User;
import warmer.star.blog.model.UserInfo;
import warmer.star.blog.model.UserRole;
import warmer.star.blog.service.UserRoleService;
import warmer.star.blog.service.UserService;
import warmer.star.blog.util.Md5Util;
import warmer.star.blog.util.RedisService;

@Configuration
@EnableWebSecurity
public class CustomAuthenticationProvider implements AuthenticationProvider {
	@Autowired
    private RedisService redisService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRoleService userRoleService;
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) authentication;
        String username = token.getName();
        //从数据库找到的用户
        User user = null;
        if (username != null) {
        	 user = userService.getUserByUsername(username);
        }
        if (user == null) {
            throw new UsernameNotFoundException("用户名/密码无效");
        } else if (user.isEnabled()) {
            throw new DisabledException("用户已被禁用");
        } else if (user.isAccountNonExpired()) {
            throw new AccountExpiredException("账号已过期");
        } else if (user.isAccountNonLocked()) {
            throw new LockedException("账号已被锁定");
        } else if (user.isCredentialsNonExpired()) {
            throw new LockedException("凭证已过期");
        }
        //数据库用户的密码
        String password = user.getPassword();
        String pwdDigest = Md5Util.pwdDigest(token.getCredentials().toString());
        //与authentication里面的credentials相比较
        if (!password.equals(pwdDigest)) {

            throw new BadCredentialsException("Invalid username/password");
        }
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        List<UserRole> userRoleList=userRoleService.getUserRole(user.getId());
        for (UserRole userRole : userRoleList) {
        	GrantedAuthority grantedAuthority = new SimpleGrantedAuthority(userRole.getRoleid().toString());
    		// 1：此处将权限信息添加到 GrantedAuthority 对象中，在后面进行全权限验证时会使用GrantedAuthority 对象。
    		grantedAuthorities.add(grantedAuthority);
		}
        UserInfo userInfo=userService.getUserInfo(username);
        redisService.remove(username);
    	redisService.set(username, JSON.toJSONString(userInfo));
        redisService.expire(username, 3600);
        //授权
        return new UsernamePasswordAuthenticationToken(userInfo, password, grantedAuthorities);
    }
    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.equals(authentication);
    }
}


