package org.apache.mesos.elasticsearch.matcher;

import org.apache.mesos.Protos;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.util.Collection;

/**
 * Matcher for {@link org.apache.mesos.Protos.Request}s
 */
public class RequestMatcher extends BaseMatcher<Collection<Protos.Request>> {

    private double cpus;

    @SuppressWarnings("unchecked")
    @Override
    public boolean matches(Object o) {
        Collection<Protos.Request> requests = (Collection<Protos.Request>) o;

        Protos.Resource cpuResource = Protos.Resource.newBuilder()
                .setName("cpus")
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(cpus).build())
                .build();

        Protos.Request request = Protos.Request.newBuilder()
                .addResources(cpuResource)
                .build();

        return requests.contains(request);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(cpus + " cpu(s)");
    }

    public RequestMatcher cpus(double cpus) {
        this.cpus = cpus;
        return this;
    }

}
