package com.example.accounts.aspect;

import com.example.accounts.tracing.BusinessContextTracer;
import io.opentelemetry.api.trace.Span;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Aspect to add OpenTelemetry tracing to all repository operations
 */
@Aspect
@Component
public class RepositoryTracingAspect {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryTracingAspect.class);

    @Autowired
    private BusinessContextTracer businessContextTracer;

    /**
     * Intercept all repository method calls and add OTEL tracing
     */
    @Around("execution(* com.example.accounts.repository.*Repository.*(..))")
    public Object traceRepositoryMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String operationName = String.format("accounts.repository.%s.%s", 
                                            className.replace("Repository", "").toLowerCase(),
                                            methodName);

        // Create child span for repository operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("account-repository", methodName)
                .transactionType(getTransactionType(methodName));

        // Add entity-specific context if we can extract it from parameters
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            addEntityContext(context, args[0], methodName);
        }

        Span span = businessContextTracer.startChildSpan(operationName, context);

        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.debug("Repository: Executing {}.{}", className, methodName);

            // Execute the actual repository method
            Object result = joinPoint.proceed();

            // Add result context
            addResultContext(span, result, methodName);

            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());

            logger.debug("Repository: Completed {}.{} in {}ms", className, methodName, duration);
            return result;

        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("REPOSITORY_OPERATION_FAILED")
                .errorCategory("DATABASE_ERROR")
                .toOtelAttributes());

            logger.error("Repository: Failed {}.{}", className, methodName, e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Determine the transaction type based on method name
     */
    private String getTransactionType(String methodName) {
        if (methodName.startsWith("save") || methodName.startsWith("insert")) {
            return "DATABASE_WRITE";
        } else if (methodName.startsWith("delete") || methodName.startsWith("remove")) {
            return "DATABASE_DELETE";
        } else if (methodName.startsWith("find") || methodName.startsWith("get") || 
                   methodName.startsWith("exists") || methodName.startsWith("count")) {
            return "DATABASE_READ";
        } else {
            return "DATABASE_OPERATION";
        }
    }

    /**
     * Add entity-specific context based on method parameters
     */
    private void addEntityContext(BusinessContextTracer.BusinessContext context, Object firstArg, String methodName) {
        try {
            if (firstArg instanceof String && methodName.contains("Id")) {
                // This is likely an ID parameter
                context.accountId((String) firstArg);
            } else if (firstArg != null && firstArg.getClass().getSimpleName().contains("Account")) {
                // This is likely an Account entity
                extractAccountContext(context, firstArg);
            }
        } catch (Exception e) {
            // Ignore reflection errors, context addition is optional
            logger.debug("Could not extract entity context: {}", e.getMessage());
        }
    }

    /**
     * Extract context from Account entity using reflection
     */
    private void extractAccountContext(BusinessContextTracer.BusinessContext context, Object account) {
        try {
            // Use reflection to get account properties
            var accountClass = account.getClass();
            var accountIdField = accountClass.getDeclaredField("accountId");
            var customerIdField = accountClass.getDeclaredField("customerId");
            var accountTypeField = accountClass.getDeclaredField("accountType");

            accountIdField.setAccessible(true);
            customerIdField.setAccessible(true);
            accountTypeField.setAccessible(true);

            String accountId = (String) accountIdField.get(account);
            String customerId = (String) customerIdField.get(account);
            String accountType = (String) accountTypeField.get(account);

            if (accountId != null) context.accountId(accountId);
            if (customerId != null) context.customerId(customerId);
            if (accountType != null) context.accountType(accountType);

        } catch (Exception e) {
            // Ignore reflection errors
            logger.debug("Could not extract account context: {}", e.getMessage());
        }
    }

    /**
     * Add result context based on method return value
     */
    private void addResultContext(Span span, Object result, String methodName) {
        try {
            if (result instanceof Collection) {
                Collection<?> collection = (Collection<?>) result;
                span.setAllAttributes(businessContextTracer.createContext()
                    .batchSize(collection.size())
                    .toOtelAttributes());
            } else if (result instanceof Boolean) {
                // For exists methods
                span.setAllAttributes(businessContextTracer.createContext()
                    .toOtelAttributes());
            } else if (result instanceof Number) {
                // For count methods
                span.setAllAttributes(businessContextTracer.createContext()
                    .batchSize(((Number) result).intValue())
                    .toOtelAttributes());
            }
        } catch (Exception e) {
            // Ignore errors in result context addition
            logger.debug("Could not add result context: {}", e.getMessage());
        }
    }
}