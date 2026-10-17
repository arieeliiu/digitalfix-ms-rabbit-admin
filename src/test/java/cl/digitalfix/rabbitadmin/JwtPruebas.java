package cl.digitalfix.rabbitadmin;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Claves efimeras y JWKS SOLO en src/test; el binario productivo no contiene bypass.
public abstract class JwtPruebas {
    static final String ISSUER = "https://issuer.pruebas.invalid";
    static final String AUDIENCE = "digitalfix-api-pruebas";
    static final RSAKey CLAVE;
    static final HttpServer JWKS;
    static {
        try {
            CLAVE = new RSAKeyGenerator(2048).keyID("prueba").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            JWKS.createContext("/jwks", request -> {
                byte[] cuerpo = new JWKSet(CLAVE.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                request.getResponseHeaders().set("Content-Type","application/json");
                request.sendResponseHeaders(200,cuerpo.length);
                try(var out = request.getResponseBody()) { out.write(cuerpo); }
            });
            JWKS.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> JWKS.stop(0)));
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        r.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCE);
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:"+JWKS.getAddress().getPort()+"/jwks");
        r.add("spring.rabbitmq.addresses", () -> System.getenv().getOrDefault("RABBITMQ_TEST_ADDRESSES","127.0.0.1:1"));
        r.add("spring.rabbitmq.username", () -> System.getenv().getOrDefault("RABBITMQ_USERNAME","pruebas"));
        r.add("spring.rabbitmq.password", () -> System.getenv().getOrDefault("RABBITMQ_PASSWORD","pruebas"));
        r.add("spring.rabbitmq.virtual-host", () -> "digitalfix");
    }

    static String token(String rol, String scope) throws Exception {
        return token(rol,scope,ISSUER,AUDIENCE,Instant.now().plusSeconds(300),CLAVE);
    }
    static String admin() throws Exception { return token("Admin","access_as_user"); }
    static String token(String rol,String scope,String issuer,String audiencia,Instant exp,RSAKey clave) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audiencia).subject("usuario-pruebas")
                .issueTime(Date.from(Instant.now().minusSeconds(10))).expirationTime(Date.from(exp))
                .claim("roles",List.of(rol)).claim("scp",scope).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("prueba").build(),claims);
        jwt.sign(new RSASSASigner(clave)); return jwt.serialize();
    }
}
