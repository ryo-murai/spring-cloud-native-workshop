package com.metflix;

import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.List;

import org.apache.catalina.filters.RequestDumperFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;

@SpringBootApplication
@EnableDiscoveryClient
@EnableCircuitBreaker
public class UiApplication extends WebSecurityConfigurerAdapter {

	public static void main(String[] args) {
		SpringApplication.run(UiApplication.class, args);
	}

	@Bean
	@LoadBalanced
	RestTemplate restTemplate() {
		return new RestTemplate();
	}
	
	
	@SuppressWarnings("deprecation")
	@Bean
	NoOpPasswordEncoder noOpPasswordEncoder() {
		return (NoOpPasswordEncoder) NoOpPasswordEncoder.getInstance();
	}
	
	@Autowired
	UserDetailsService userDetailsService;
	
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.httpBasic()
			.and()
			.csrf().ignoringAntMatchers("/actuator/env**", "/actuator/refresh**")
			.and()
			.authorizeRequests()
			.antMatchers("/actuator/env**", "/actuator/refresh**", "/actuator/hystrix**").permitAll()
			.antMatchers("**").authenticated()
			.and()
			.addFilterBefore(new RequestDumperFilter(), ChannelProcessingFilter.class);
	}
	
	@Override
	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(userDetailsService);
	}
}

class Movie {
	public String title;
}

@RefreshScope
@Controller
class HomeController {
	@Autowired
	RecommendationService recommendationService;
	
    @Value("${message:Welcome to Metflix!}")
    String message;
	
	@RequestMapping("/")
	String home(Principal principal, Model model) {
		List<Movie> recommendations = 
				recommendationService.getRecommendations(principal.getName());
		model.addAttribute("message", message);
		model.addAttribute("username", principal.getName());
		model.addAttribute("recommendations", recommendations);
		return "index";
	}
}

@Component
class RecommendationService {
    @Autowired
    RestTemplate restTemplate;
    @Value("${recommendation.api:http://localhost:3333}")
    URI recommendationApi;

     @HystrixCommand(fallbackMethod = "getRecommendationsFallback",
            commandProperties = @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "5000"))
    public List<Movie> getRecommendations(String username) {
 		URI uri = UriComponentsBuilder.fromUri(recommendationApi)
				.pathSegment("api", "recommendations", username)
				.build()
				.toUri();
        return restTemplate.exchange(RequestEntity.get(uri).build(), new ParameterizedTypeReference<List<Movie>>() {
        }).getBody();
    }

    @SuppressWarnings("unused")
	private List<Movie> getRecommendationsFallback(String username) {
        return Collections.emptyList();
    }
}

@Component
class MemberUserDetailService implements UserDetailsService {
	@Autowired
	RestTemplate restTemplate;
	
	@Value("${member.api:http://localhost:4444}")
	URI memberApi;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		URI uri = UriComponentsBuilder
				.fromUri(memberApi)
				.pathSegment("api", "members", username)
				.build()
				.toUri();

		String member = restTemplate.exchange(RequestEntity.get(uri).build(), String.class).getBody();
		if(member == null) {
			throw new UsernameNotFoundException(username);
		}
		
		return new User(username, "metflix", AuthorityUtils.createAuthorityList("MEMBER"));
	}
}