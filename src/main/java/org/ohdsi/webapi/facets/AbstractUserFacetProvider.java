package org.ohdsi.webapi.facets;

import org.ohdsi.webapi.shiro.Entities.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractUserFacetProvider extends AbstractColumnBasedFacetProvider {
    protected final UserRepository userRepository;

    public AbstractUserFacetProvider(JdbcTemplate jdbcTemplate, UserRepository userRepository) {
        super(jdbcTemplate);
        this.userRepository = userRepository;
    }

    @Override
    public <T> Predicate createFacetPredicate(List<FacetItem> items, CriteriaBuilder criteriaBuilder, Root<T> root) {
        assert items != null && !items.isEmpty();
        final List<Long> ids = items.stream().map(item -> Long.valueOf(item.key)).collect(Collectors.toList());
        final Path<Object> path = root.get(getField());
        if(ids.contains(0L)) {
            return criteriaBuilder.or(path.in(ids), path.isNull());
        } else {
            return path.in(ids);
        }
    }

    @Override
    public <T> Predicate createTextSearchPredicate(String field, String text, CriteriaBuilder criteriaBuilder, Root<T> root) {
        final Path<String> userName = root.get(getField()).get("name");
        return criteriaBuilder.like(userName, '%' + text + '%');
    }

    protected abstract String getField();

    @Override
    protected String getKey(ResultSet resultSet) throws SQLException {
        return String.valueOf(resultSet.getInt(1));
    }

    @Override
    protected String getText(ResultSet resultSet) throws SQLException {
        final String login = userRepository.getUserLoginById((long) resultSet.getInt(1));
        return login != null ? login : "anonymous";
    }
}