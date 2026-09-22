package io.openmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;

import java.net.URI;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteDefinitionDto {

    @NotBlank(message = "Route id must not be blank")
    private String id;

    @NotBlank(message = "Tenant URI must not be blank")
    private String uri;

    @Builder.Default
    private List<String> predicates = new ArrayList<>();

    @Builder.Default
    private List<String> filters = new ArrayList<>();

    @Builder.Default
    private int order = 0;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public static RouteDefinitionDto fromRouteDefinition(RouteDefinition routeDefinition) {
        List<String> predicateTexts = new ArrayList<>();

        if (routeDefinition.getPredicates() != null) {
            for (PredicateDefinition pred : routeDefinition.getPredicates()) {
                predicateTexts.add(formatPredicate(pred));
            }
        }

        List<String> filterTexts = new ArrayList<>();
        if (routeDefinition.getFilters() != null) {
            for (FilterDefinition filter : routeDefinition.getFilters()) {
                filterTexts.add(formatFilter(filter));
            }
        }

        return RouteDefinitionDto.builder()
                .id(routeDefinition.getId())
                .uri(routeDefinition.getUri() != null ? routeDefinition.getUri().toString() : "")
                .predicates(predicateTexts)
                .filters(filterTexts)
                .order(routeDefinition.getOrder())
                .metadata(routeDefinition.getMetadata() != null ? new HashMap<>(routeDefinition.getMetadata())
                        : new HashMap<>())
                .build();

    }

    private static String formatPredicate(PredicateDefinition pred) {
        // Implementation for formatting predicate
        if (pred == null)
            return "";

        if (pred.getArgs() == null || pred.getArgs().isEmpty())
            return pred.getName();

        boolean positional = pred.getArgs().keySet().stream().allMatch(k -> k.startsWith("_genkey_"));
        if (positional) {
            return pred.getName() + "=" + String.join(",", pred.getArgs().values());
        }
        StringJoiner sj = new StringJoiner(",");
        pred.getArgs().forEach((k, v) -> sj.add(k + ":" + v));
        return pred.getName();
    }
    



    private static String formatFilter(FilterDefinition filter) {
        // Implementation for formatting filter
        if (filter == null)
            return "";

        if (filter.getArgs() == null || filter.getArgs().isEmpty())
            return filter.getName();

        boolean positional = filter.getArgs().keySet().stream().allMatch(k -> k.startsWith("_genkey_"));
        if (positional) {
            return filter.getName() + "=" + String.join(",", filter.getArgs().values());
        }
        StringJoiner sj = new StringJoiner(",");
        filter.getArgs().forEach((k, v) -> sj.add(k + ":" + v));
        return filter.getName();
    }





    public RouteDefinition toRouteDefinition() {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(this.id);
        definition.setUri(this.uri != null ? URI.create(this.uri) : null);
        definition.setOrder(this.order);

        List<PredicateDefinition> predicateDefs = new ArrayList<>();
        if (this.predicates != null) {
            for (String predicateText : this.predicates) {
                predicateDefs.add(new PredicateDefinition(predicateText));
            }
        }
        definition.setPredicates(predicateDefs);

        List<FilterDefinition> filterDefs = new ArrayList<>();
        if (this.filters != null) {
            for (String filterText : this.filters) {
                filterDefs.add(new FilterDefinition(filterText));
            }
        }
        definition.setFilters(filterDefs);

        if (this.metadata != null) {
            definition.setMetadata(new HashMap<>(this.metadata));
        }

        return definition;
    }
}
