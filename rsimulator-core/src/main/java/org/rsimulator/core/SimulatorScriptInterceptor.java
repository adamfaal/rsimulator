package org.rsimulator.core;

import com.google.inject.Singleton;
import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * SimulatorScriptInterceptor is an interceptor that supports Groovy scripts intercepting invocations of
 * {@link Simulator#service(String, String, String, String)}.
 * <p>
 * The scripts supported are:
 * <ol>
 * <li>GlobalRequest.groovy; Must be put in the rootPath folder and is applied before the invocation.</li>
 * <li>&lt;TestName&gt;.groovy; Must be put in the same folder as test request and response and is applied first after
 * the invocation. The name of the groovy file must be the same as for the test request, e.g. Test1Request.txt -
 * Test1.groovy.</li>
 * <li>GlobalResponse.groovy; Must be put in the rootPath folder and is applied last after the invocation.</li>
 * </ol>
 * <p>
 * All script have a Map&lt;String, Object&gt; available through the variable vars. The keys contentType, simulatorResponse,
 * request, rootPath, rootRelativePath can be used to access invocation arguments and return value. In addition, the map
 * can be used to communicate arbitrary objects between the Groovy scripts. If a script sets a SimulatorResponse in the
 * vars map, this SimulatorResponse is directly returned.
 */
@Singleton
public class SimulatorScriptInterceptor implements MethodInterceptor {
    private static final int CONTENT_TYPE_INDEX = 3;
    private static final int REQUEST_INDEX = 2;
    private static final int ROOT_RELATIVE_PATH_INDEX = 1;
    private static final int ROOT_PATH_INDEX = 0;
    private static final String CONTENT_TYPE = "contentType";
    private static final String SIMULATOR_RESPONSE_OPTIONAL = "simulatorResponseOptional";
    private static final String REQUEST = "request";
    private static final String ROOT_PATH = "rootPath";
    private static final String ROOT_RELATIVE_PATH = "rootRelativePath";
    private static final String GROOVY_PATTERN = org.rsimulator.core.config.Constants.REQUEST + ".*";
    private Logger log = LoggerFactory.getLogger(SimulatorScriptInterceptor.class);

    private enum Scope {
        GLOBAL_REQUEST, GLOBAL_RESPONSE, LOCAL_RESPONSE
    }

    public Object invoke(MethodInvocation invocation) throws Throwable {
        log.debug("Arguments are {}", invocation.getArguments());
        Map<String, Object> vars = new HashMap<String, Object>();

        vars.put(ROOT_PATH, invocation.getArguments()[ROOT_PATH_INDEX]);
        vars.put(ROOT_RELATIVE_PATH, invocation.getArguments()[ROOT_RELATIVE_PATH_INDEX]);
        vars.put(REQUEST, invocation.getArguments()[REQUEST_INDEX]);
        vars.put(CONTENT_TYPE, invocation.getArguments()[CONTENT_TYPE_INDEX]);

        applyScript(Scope.GLOBAL_REQUEST, vars);
        Optional<SimulatorResponse> simulatorResponseOptional = (Optional<SimulatorResponse>) vars.get(SIMULATOR_RESPONSE_OPTIONAL);
        if (simulatorResponseOptional != null && simulatorResponseOptional.isPresent()) {
            log.debug("Returning {}", simulatorResponseOptional.get());
            return simulatorResponseOptional;
        }

        invocation.getArguments()[ROOT_PATH_INDEX] = vars.get(ROOT_PATH);
        invocation.getArguments()[ROOT_RELATIVE_PATH_INDEX] = vars.get(ROOT_RELATIVE_PATH);
        invocation.getArguments()[REQUEST_INDEX] = vars.get(REQUEST);
        invocation.getArguments()[CONTENT_TYPE_INDEX] = vars.get(CONTENT_TYPE);
        
        simulatorResponseOptional = (Optional<SimulatorResponse>) invocation.proceed();

        vars.put(SIMULATOR_RESPONSE_OPTIONAL, simulatorResponseOptional);
        applyScript(Scope.LOCAL_RESPONSE, vars);
        applyScript(Scope.GLOBAL_RESPONSE, vars);
        return vars.get(SIMULATOR_RESPONSE_OPTIONAL);
    }

    private void applyScript(Scope type, Map<String, Object> vars) {
        try {
            String root = null;
            String script = null;
            switch (type) {
                case GLOBAL_REQUEST:
                    root = (String) vars.get(ROOT_PATH);
                    script = "GlobalRequest.groovy";
                    break;
                case GLOBAL_RESPONSE:
                    root = (String) vars.get(ROOT_PATH);
                    script = "GlobalResponse.groovy";
                    break;
                case LOCAL_RESPONSE:
                    Optional<SimulatorResponse> simulatorResponseOptional = (Optional<SimulatorResponse>) vars.get(SIMULATOR_RESPONSE_OPTIONAL);
                    if (simulatorResponseOptional.isPresent()) {
                        Path matchingRequest = simulatorResponseOptional.get().getMatchingRequest();
                        root = matchingRequest.getParent().toAbsolutePath().toString();
                        script = matchingRequest.getFileName().toString().replaceAll(GROOVY_PATTERN, ".groovy");
                    }
                    break;
                default:
                    break;
            }
            File file = new File(String.join(File.separator, root, script));
            if (file.exists()) {
                log.debug("Applying script {} of type: {}, with vars: {}", new Object[]{file, type, vars});
                String[] roots = new String[]{root};
                GroovyScriptEngine gse = new GroovyScriptEngine(roots);
                Binding binding = new Binding();
                binding.setVariable("vars", vars);
                gse.run(script, binding);
                log.debug("Applied script {} of type: {}, and updated vars are: {}", new Object[]{file, type, vars});
            } else {
                log.debug("When applying script of type {}, script path {} is not an existing file", type, root);
            }
        } catch (Exception e) {
            log.error("Script error.", e);
        }
    }
}
