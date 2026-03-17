package com.shortform.backend.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.render-queue}")
    private String renderQueue;

    @Value("${app.rabbitmq.preview-queue}")
    private String previewQueue;

    @Bean
    public DirectExchange shortformExchange() {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue renderQueue() {
        return QueueBuilder.durable(renderQueue).build();
    }

    @Bean
    public Queue previewQueue() {
        return QueueBuilder.durable(previewQueue).build();
    }

    @Bean
    public Binding renderBinding(Queue renderQueue, DirectExchange shortformExchange) {
        return BindingBuilder.bind(renderQueue).to(shortformExchange).with("render");
    }

    @Bean
    public Binding previewBinding(Queue previewQueue, DirectExchange shortformExchange) {
        return BindingBuilder.bind(previewQueue).to(shortformExchange).with("preview");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
