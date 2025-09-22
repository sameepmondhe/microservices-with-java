package com.example.loans.aspect;

import com.example.loans.tracing.BusinessContextTracer;
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
    @Around("execution(* com.example.loans.repository.*Repository.*(..))")
    public Object traceRepositoryMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String operationName = String.format("loans.repository.%s.%s", 
                                            className.replace("Repository", "").toLowerCase(),
                                            methodName);

        // Create child span for repository operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loan-repository", methodName)
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
                // This is likely an ID parameter - use it as loan context
                // Note: We don't have a specific loanId method in BusinessContext yet
                context.customerId((String) firstArg); // Use customerId for now
            } else if (firstArg != null && firstArg.getClass().getSimpleName().contains("Loan")) {
                // This is likely a Loan entity
                extractLoanContext(context, firstArg);
            }
        } catch (Exception e) {
            // Ignore reflection errors, context addition is optional
            logger.debug("Could not extract entity context: {}", e.getMessage());
        }
    }

    /**
     * Extract context from Loan entity using reflection
     */
    private void extractLoanContext(BusinessContextTracer.BusinessContext context, Object loan) {
        try {
            // Use reflection to get loan properties
            var loanClass = loan.getClass();
            var loanIdField = loanClass.getDeclaredField("loanId");
            var customerIdField = loanClass.getDeclaredField("customerId");
            var loanAmountField = loanClass.getDeclaredField("loanAmount");

            loanIdField.setAccessible(true);
            customerIdField.setAccessible(true);
            loanAmountField.setAccessible(true);

            String loanId = (String) loanIdField.get(loan);
            String customerId = (String) customerIdField.get(loan);
            String loanAmount = (String) loanAmountField.get(loan);

            // Note: BusinessContextTracer might not have loanId method, using available methods
            if (customerId != null) context.customerId(customerId);
            // We can add the loanId as a custom attribute or extend BusinessContextTracer

        } catch (Exception e) {
            // Ignore reflection errors
            logger.debug("Could not extract loan context: {}", e.getMessage());
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