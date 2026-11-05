package com.aiadaptive.config;

import com.aiadaptive.security.AuthContext;
import com.aiadaptive.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor interceptor;

    public WebConfig(AuthInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }

    /** Lets controllers declare an {@link AuthContext} parameter directly. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter p) {
                return AuthContext.class.equals(p.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter p, ModelAndViewContainer m,
                                          NativeWebRequest request, WebDataBinderFactory f) {
                return request.getAttribute(AuthInterceptor.ATTR, RequestAttributes.SCOPE_REQUEST);
            }
        });
    }
}
