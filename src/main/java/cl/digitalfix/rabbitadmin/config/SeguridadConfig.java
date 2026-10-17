package cl.digitalfix.rabbitadmin.config;

import java.util.ArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.*;
import static org.springframework.security.authorization.AuthorizationManagers.allOf;

@Configuration
public class SeguridadConfig {
    @Bean
    JwtAuthenticationConverter autoridades() {
        var scopes = new JwtGrantedAuthoritiesConverter();
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles"); roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var resultado = new ArrayList<GrantedAuthority>();
            resultado.addAll(scopes.convert(jwt)); resultado.addAll(roles.convert(jwt));
            return resultado;
        });
        return converter;
    }

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, JwtAuthenticationConverter autoridades) throws Exception {
        AuthenticationEntryPoint noAutenticado = (req,res,error) -> {
            res.setStatus(401); res.setHeader("WWW-Authenticate","Bearer");
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"status\":401,\"codigo\":\"NO_AUTENTICADO\",\"mensaje\":\"Se requiere un JWT valido\"}");
        };
        AccessDeniedHandler sinPermiso = (req,res,error) -> {
            res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"status\":403,\"codigo\":\"SIN_PERMISO\",\"mensaje\":\"Se requieren rol Admin y scope access_as_user\"}");
        };
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(r -> r.requestMatchers(HttpMethod.GET,"/healthz").permitAll()
                        .requestMatchers("/api/rabbit/**").access(allOf(hasRole("Admin"),hasAuthority("SCOPE_access_as_user")))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(r -> r.jwt(j -> j.jwtAuthenticationConverter(autoridades))
                        .authenticationEntryPoint(noAutenticado).accessDeniedHandler(sinPermiso))
                .exceptionHandling(e -> e.authenticationEntryPoint(noAutenticado).accessDeniedHandler(sinPermiso)).build();
    }
}
