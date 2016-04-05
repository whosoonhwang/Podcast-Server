package lan.dk.podcastserver.config;

import org.apache.tika.Tika;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static lan.dk.podcastserver.service.MimeTypeService.TikaProbeContentType;

/**
 * Created by kevin on 26/12/2013.
 */
@Configuration
@ComponentScan(basePackages = { "lan.dk.podcastserver.utils", "lan.dk.podcastserver.service", "lan.dk.podcastserver.business"})
public class BeanConfigScan {

    @Bean(name="Validator")
    public LocalValidatorFactoryBean validator() {
        return new LocalValidatorFactoryBean();
    }


    @Bean
    public TikaProbeContentType tikaProbeContentType() {
        return new TikaProbeContentType(new Tika());
    }
}
