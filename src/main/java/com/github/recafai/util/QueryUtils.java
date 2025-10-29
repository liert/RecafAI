package com.github.recafai.util;

import jakarta.annotation.Nonnull;
import software.coley.recaf.services.search.match.StringPredicate;
import software.coley.recaf.services.search.match.StringPredicateProvider;
import software.coley.recaf.services.search.query.Query;
import software.coley.recaf.services.search.query.ReferenceQuery;
import software.coley.recaf.services.search.query.StringQuery;
import software.coley.recaf.util.RegexUtil;

public class QueryUtils {
    public static StringPredicateProvider stringPredicateProvider;

    public static Query stringSearch(String id, String search) {
        StringPredicate predicate = buildPredicate(id, search);
        if (predicate == null)
            return null;

        return new StringQuery(predicate);
    }

    public static Query classReferenceSearch(String id, String search) {
        StringPredicate predicate = buildPredicate(id, search);
        if (predicate == null)
            return null;

        return new ReferenceQuery(predicate);
    }

    public static Query memberReferenceSearch(String ownerId, String ownerSearch,
                                              String nameId, String nameSearch) {

        StringPredicate ownerPredicate = buildPredicate(ownerId, ownerSearch);
        StringPredicate namePredicate = buildPredicate(nameId, nameSearch);
        // StringPredicate descPredicate = buildPredicate(descId, descSearch);

        // Skip if no predicates are provided
        if (ownerPredicate == null && namePredicate == null)
            return null;

        return new ReferenceQuery(ownerPredicate, namePredicate, null);
    }

    private static StringPredicate buildPredicate(@Nonnull String id, @Nonnull String text) {
        if (text.isBlank())
            return null;

        if (id.contains("regex") && !RegexUtil.validate(text).valid())
            return null;

        return stringPredicateProvider.newBiStringPredicate(id, text);
    }
}
