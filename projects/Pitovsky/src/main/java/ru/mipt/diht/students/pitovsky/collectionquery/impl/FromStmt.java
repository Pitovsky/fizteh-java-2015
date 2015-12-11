package ru.mipt.diht.students.pitovsky.collectionquery.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class FromStmt<T> {
    private Iterable<T> base;
    private ExecutedTable<?> previousTable = null;

    private FromStmt() { }

    private static final class ExecutedTable<R> {
        private final Iterable<R> outputTable;
        private final Class<R> outputClass;

        private ExecutedTable(Class<R> clazz, Iterable<R> table) {
            outputTable = table;
            outputClass = clazz;
        }
    }

    public static <T> FromStmt<T> from(Iterable<T> iterable) {
        FromStmt<T> stmt = new FromStmt<>();
        stmt.base = iterable;
        return stmt;
    }

    public static <T> FromStmt<T> from(Stream<T> stream) {
        FromStmt<T> stmt = new FromStmt<>();
        List<T> list = new ArrayList<>();
        stream.forEach(s -> list.add(s));
        stmt.base = list;
        return stmt;
    }

    public static <T> FromStmt<T> from(WhereStmt<?, T> subStmt) throws CollectionQueryExecuteException {
        Iterable<T> subQueryResult = subStmt.execute();
        return from(subQueryResult);
    }

    <R> void setPreviousPart(Class<R> clazz, Iterable<R> table) {
        previousTable = new ExecutedTable<R>(clazz, table);
    }

    private <R> SelectStmt<T, R> innerSelect(Class<R> clazz, Function<T, ?>[] s, boolean isDistinct)
            throws CollectionQuerySyntaxException {
        if (previousTable == null) {
            return new SelectStmt<T, R>(base, null, clazz, isDistinct, s);
        } else {
            if (!previousTable.outputClass.equals(clazz)) {
                throw new CollectionQuerySyntaxException("parts of union has different types");
            }
            return new SelectStmt<T, R>(base, (Iterable<R>) previousTable.outputTable, clazz, isDistinct, s);
        }
    }

    /**
     * Get rows, which applies for conditions.
     * @param clazz class, which able to store. Must have constructor from functions returns types (see below)
     * @param s parameters and aggregates for output
     * @return select statement for execution
     * @throws CollectionQuerySyntaxException
     */
    @SafeVarargs
    public final <R> SelectStmt<T, R> select(Class<R> clazz, Function<T, ?>... s)
            throws CollectionQuerySyntaxException {
        return innerSelect(clazz, s, false);
    }

    @SafeVarargs
    public final <R> SelectStmt<T, R> selectDistinct(Class<R> clazz, Function<T, ?>... s)
            throws CollectionQuerySyntaxException {
        return innerSelect(clazz, s, true);
    }

    public static final class Tuple<F, S> {
        private F firstPart;
        private S secondPart;

        private Tuple(F first, S second) {
            firstPart = first;
            secondPart = second;
        }

        public F first() {
            return firstPart;
        }

        public S second() {
            return secondPart;
        }
    }


    public final class JoinStmt<S> {
        private Iterable<S> secondTable;

        private JoinStmt(Iterable<S> tableOnJoin) {
            secondTable = tableOnJoin;
        }

        public FromStmt<Tuple<T, S>> on(Predicate<Tuple<T, S>> joiningPredicate) {
            Collection<Tuple<T, S>> joinedCollection = new ArrayList<>();
            for (T first : base) { //todo: it must be linear (hash mapping by predicate, i don't know how
                for (S second : secondTable) {
                    Tuple<T, S> joinedRow = new Tuple<T, S>(first, second);
                    if (joiningPredicate.test(joinedRow)) {
                        joinedCollection.add(joinedRow);
                    }
                }
            }
            FromStmt<Tuple<T, S>> stmt = new FromStmt<>();
            stmt.base = joinedCollection;
            stmt.previousTable = previousTable;
            return stmt;
        }
    }

    public <S> JoinStmt<S> join(Iterable<S> secondTable) {
        return new JoinStmt<>(secondTable);
    }
}
