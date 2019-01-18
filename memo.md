## spring-boot.md
### UI
* BASIC認証が失敗し401になる事象
  - No password encoderうんたらかんたら のエラー
  - UiApplication.java
```java
	// add below code in UiApplication class
	@SuppressWarnings("deprecation")
	@Bean
	NoOpPasswordEncoder noOpPasswordEncoder() {
		return (NoOpPasswordEncoder) NoOpPasswordEncoder.getInstance();
	}
	// `NoOpPasswordEncoder`はdeprecatedなので、本来の対処ではなく暫定対処
```

## config-server.md
### Config-Server
* github.comからファイルがアクセスできない
  - https proxyが設定されていないため
  - `application.properties`に以下を追加。Boot起動時にプロキシユーザ/PWを指定
    + `-Dproxy_user=xxxxx -Dproxy_password=xxxx`

  ```
	spring.cloud.config.server.git.skip-ssl-validation=true
	# hostname of proxy server
	spring.cloud.config.server.git.proxy.https.host=localhost
	# port number of proxy server
	spring.cloud.config.server.git.proxy.https.port=8888
	# proxy auth user name
	spring.cloud.config.server.git.proxy.https.username=${proxy_user}
	# proxy auth password
	spring.cloud.config.server.git.proxy.https.password=${proxy_password}
  ```

* Config-Server: endpoint `/env`にアクセスできない
  - Cloud Config 2.0の変更点？で、prefixとして `/actuator/`パスとなる。
  - また、endpoint `env`は明示的に`web.exposure`にincludeしないといけない
    + https://docs.spring.io/spring-boot/docs/2.1.x/reference/html/production-ready-endpoints.html#production-ready-endpoints-exposing-endpoints
  - add below line in application.properties

	```
	management.endpoints.web.exposure.include=*
	```

* from browser, access `http://localhost:8888/actuator/env` instead `http://localhost:8888/env`.
* if `$http_proxy` is set in git-bash, use `curl -noproxy localhost` to bypass your proxy.

### Config-Client
* Config-Client: endpoint `/env`, `/refresh`にアクセスできない
  - 上述の通り、endpointは `/actuator/*`となっている
  - UiApplication.java spring securityの許可パスを変更

  ```java
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.httpBasic()
			.and()
			.csrf().ignoringAntMatchers("/actuator/env**", "/actuator/refresh**")
			.and()
			.authorizeRequests()
			.antMatchers("/actuator/env**", "/actuator/refresh**").permitAll()
			.antMatchers("**").authenticated()
			.and()
			.addFilterBefore(new RequestDumperFilter(), ChannelProcessingFilter.class);
	}
	```

* configの上書き(`/actuator/env`へのPOST)は、チュートリアルのコマンド(x-www-form-urlencoded)だとエラーとなった
```
{"timestamp":"2019-01-18T04:27:56.075+0000","status":415,
 "error":"Unsupported Media Type",
 "message":"Content type 'application/x-www-form-urlencoded' not supported",
 "path":"/actuator/env"}
```

  - stack over flow投稿(URL失念)を参考に、JSONで送ると成功。その時に、`name`にキー、`value`に値を指定したJSONとする必要がある
   ```sh
   curl --noproxy localhost -H "Content-Type: application/json" \
   -X POST localhost:8080/actuator/env \
   -d '{"name": "message", " value": "Message is updated"}'
   ```

* refreshも同様に `/actuator/refresh`とする


### service-registry
#### 最初に
* git repository側に service-registryブランチを作成して、application.properties等ファイルを編集するという手順がチュートリアルに書いていない（後の方の手順で、「サービル論理名を使っていることに着目」って書いてる・・・）

#### Eureka-Client
* `membership`, `recommendations`, `ui`の pom.xml は use `spring-cloud-starter-netflix-eureka-client` instead of `spring-cloud-starter-eureka`

#### Eureka-Server(上述ブランチ対応していれば問題ない)
* tutorial設定だと、port=8080で起動しようとする
* 自身がEureka-Clientとして http://localhost:8761/eureka に接続しようとして定期的にエラーログを出力する

```txt
com.netflix.discovery.shared.transport.TransportException: Cannot execute request on any known server
	at com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient.execute(RetryableEurekaHttpClient.java:112) ~[eureka-client-1.9.8.jar:1.9.8]

	以下略
```

* 公式Referenceを参考に、bootstrap.propertiesに以下の行を追加
  - https://cloud.spring.io/spring-cloud-static/spring-cloud-netflix/2.1.0.RC3/single/spring-cloud-netflix.html#spring-cloud-eureka-server-standalone-mode

```
server.port=8761

eureka.instance.hostname=localhost
eureka.client.registerWithEureka=false
eureka.client.fetchRegistry=false
eureka.client.serviceUrl.defaultZone=http://${eureka.instance.hostname}:${server.port}/eureka/
```

* これにより、ブラウザで `http://localhost:8761/`にて、Eurekaの管理画面にアクセスできる


* `#spring.cloud.config.label=service-registry`

https://github.com/making/metflix/compare/02-config-server...03-service-registry


### circuit-breaker

#### circuit-breaker client side
* insetad of `spring-cloud-starter-hystrix`, use `spring-cloud-starter-netflix-hystrix`
* stop recommendations cause 500 at ui ... even though stopping members cause fallback in recommendations.
* got 404 in accessing to both http://localhost:3333/hystrix.stream and http://localhost:3333/actuator/hystrix.stream
