package com.metflix;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.catalina.filters.RequestDumperFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class MembershipApplication {

	public static void main(String[] args) {
		SpringApplication.run(MembershipApplication.class, args);
	}

	@Bean
	RequestDumperFilter requestDumperFilter() {
		return new RequestDumperFilter();
	}
}

class Member {
	public String user;
	public Integer age;
	
	public Member() {
	}
	
	Member(String user, Integer age) {
		this.user = user;
		this.age = age;
	}

//	String getUser() {
//		return this.user;
//	}
//	
//	Integer getAge() {
//		return this.age;
//	}
//	
//	void setUser(String user) {
//		this.user = user;
//	}
//	
//	void setAge(Integer age) {
//		this.age = age;
//	}
}

@RestController
@RequestMapping("/api/members")
class MembershipController {
	final Map<String, Member> memberStore = new ConcurrentHashMap<String, Member>() {
		private static final long serialVersionUID = 1L;

		{
			put("making", new Member("making", 10));
			put("tichimura", new Member("tichimura", 30));
		}
	};
	
	@RequestMapping(method = RequestMethod.POST)
	public Member register(@RequestBody Member member) {
		memberStore.put(member.user, member);
		return member;
	}
	
	@RequestMapping("/{user}")
	Member get(@PathVariable String user) {
		return memberStore.get(user);
	}
}
