package com.aol.cyclops.reactor;



import com.aol.cyclops2.types.anyM.transformers.FoldableTransformerSeq;

import com.aol.cyclops2.types.foldable.CyclopsCollectable;
import com.aol.cyclops2.types.foldable.To;
import com.aol.cyclops2.types.traversable.FoldableTraversable;
import com.aol.cyclops2.types.traversable.Traversable;
import cyclops.collections.immutable.LinkedListX;
import cyclops.collections.immutable.VectorX;
import cyclops.collections.mutable.ListX;
import cyclops.control.Maybe;
import cyclops.function.Fn3;
import cyclops.function.Fn4;
import cyclops.function.Monoid;
import cyclops.monads.AnyM;
import cyclops.monads.Witness;
import cyclops.monads.WitnessType;

import cyclops.stream.ReactiveSeq;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Stream;


/**
 * Monad Transformer for Java Fluxs and related types such as ReactiveSeq
 *
 * FluxT allows the deeply wrapped Flux to be manipulating within it's nested /contained context
 * @author johnmcclean
 *
 * @param <T> Type of data stored inside the nested  Fluxs
 */
public class FluxT<W extends WitnessType<W>,T> implements To<FluxT<W,T>>,
        FoldableTransformerSeq<W,T> {

    final AnyM<W,Flux<T>> run;



    private FluxT(final AnyM<W,? extends Flux<T>> run) {
        this.run = AnyM.narrow(run);
    }



    public <R> AnyM<W, R> visit(Function<? super ReactiveSeq<T>, ? extends R> rsFn,
                                Function<? super Flux<T>, ? extends R> sFn) {

        return this.transformerStream()
                .map(t -> {
                    if (t instanceof ReactiveSeq)
                        return rsFn.apply((ReactiveSeq<T>) t);
                    else
                        return sFn.apply((Flux<T>)t);

                });
    }

    /**
     * @return The wrapped AnyM
     */
    public AnyM<W,Flux<T>> unwrap() {
        return run;
    }
    public <R> R unwrapTo(Function<? super AnyM<W,Flux<T>>,? extends R> fn) {
        return unwrap().to(fn);
    }

    /**
     * Peek at the current value of the List
     * <pre>
     * {@code
     *    ListT.of(AnyM.fromFlux(Arrays.asList(10))
     *             .peek(System.out::println);
     *
     *     //prints 10
     * }
     * </pre>
     *
     * @param peek  Consumer to accept current value of List
     * @return ListT with peek call
     */
    @Override
    public FluxT<W,T> peek(final Consumer<? super T> peek) {
        return map(a -> {
            peek.accept(a);
            return a;
        });

    }

    /**
     * Filter the wrapped List
     * <pre>
     * {@code
     *    ListT.of(AnyM.fromFlux(Arrays.asList(10,11))
     *             .filter(t->t!=10);
     *
     *     //ListT<AnyM<Flux<List[11]>>>
     * }
     * </pre>
     * @param test Predicate to filter the wrapped List
     * @return ListT that applies the provided filter
     */
    @Override
    public FluxT<W,T> filter(final Predicate<? super T> test) {
        return of(run.map(seq -> seq.filter(test)));
    }

    /**
     * Map the wrapped List
     *
     * <pre>
     * {@code
     *  ListT.of(AnyM.fromFlux(Arrays.asList(10))
     *             .map(t->t=t+1);
     *
     *
     *  //ListT<AnyM<Flux<List[11]>>>
     * }
     * </pre>
     *
     * @param f Mapping function for the wrapped List
     * @return ListT that applies the map function to the wrapped List
     */
    @Override
    public <B> FluxT<W,B> map(final Function<? super T, ? extends B> f) {
        return of(run.map(o -> o.map(f)));
    }

    @Override
    public <B> FluxT<W,B> flatMap(final Function<? super T, ? extends Iterable<? extends B>> f) {
        return new FluxT<W,B>(
                run.map(o -> o.flatMap(f.andThen(ReactiveSeq::fromIterable))));

    }

    /**
     * Flat Map the wrapped List
     * <pre>
     * {@code
     *  ListT.of(AnyM.fromFlux(Arrays.asList(10))
     *             .flatMap(t->List.empty();
     *
     *
     *  //ListT<AnyM<Flux<List.empty>>>
     * }
     * </pre>
     * @param f FlatMap function
     * @return ListT that applies the flatMap function to the wrapped List
     */
    public <B> FluxT<W,B> flatMapT(final Function<? super T, FluxT<W,B>> f) {

        return of(run.map(list -> list.flatMap(a -> Flux.from(f.apply(a).run))
                .flatMap(a -> a)));
    }






    /**
     * Construct an ListT from an AnyM that contains a monad type that contains type other than List
     * The values in the underlying monad will be mapped to List<A>
     *
     * @param anyM AnyM that doesn't contain a monad wrapping an List
     * @return ListT
     */
    public static <W extends WitnessType<W>,A> FluxT<W,A> fromAnyM(final AnyM<W,A> anyM) {
        return of(anyM.map(Flux::just));
    }

    /**
     * Construct an ListT from an AnyM that wraps a monad containing  Lists
     *
     * @param monads AnyM that contains a monad wrapping an List
     * @return ListT
     */
    public static <W extends WitnessType<W>,A> FluxT<W,A> of(final AnyM<W,? extends Flux<A>> monads) {
        return new FluxT<>(
                monads);
    }
    public static <W extends WitnessType<W>,A> FluxT<W,A> ofList(final AnyM<W,? extends List<A>> monads) {
        return new FluxT<>(
                monads.map(Flux::fromIterable));
    }
    public static <A> FluxT<ReactorWitness.flux,A> fromFlux(final Flux<? extends Flux<A>> nested) {
        return of(Fluxs.anyM(nested));
    }

    public static <A> FluxT<Witness.optional,A> fromOptional(final Optional<? extends Flux<A>> nested) {
        return of(AnyM.fromOptional(nested));
    }
    public static <A> FluxT<Witness.maybe,A> fromMaybe(final Maybe<? extends Flux<A>> nested) {
        return of(AnyM.fromMaybe(nested));
    }
    public static <A> FluxT<Witness.list,A> fromList(final List<? extends Flux<A>> nested) {
        return of(AnyM.fromList(nested));
    }
    public static <A> FluxT<Witness.set,A> fromSet(final Set<? extends Flux<A>> nested) {
        return of(AnyM.fromSet(nested));
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("ListT[%s]",  run.unwrap().toString());

    }

    /* (non-Javadoc)
     * @see com.aol.cyclops2.types.Pure#unit(java.lang.Object)
     */
    public <T> FluxT<W,T> unit(final T unit) {
        return of(run.unit(Flux.just(unit)));
    }

    @Override
    public ReactiveSeq<T> stream() {
        return run.stream()
                  .flatMap(e -> e.toStream());
    }

    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops2.types.reactiveFlux.CyclopsCollectable#collectable()
     
    @Override
    public Collectable<T> collectable() {
       return this;
    } */
    @Override
    public <R> FluxT<W,R> unitIterator(final Iterator<R> it) {
        return of(run.unitIterator(it)
                .map(i -> Flux.just(i)));
    }

    @Override
    public <R> FluxT<W,R> empty() {
        return of(run.empty());
    }

    @Override
    public AnyM<W,? extends FoldableTraversable<T>> nestedFoldables() {
        return run.map(ReactiveSeq::fromPublisher);

    }

    @Override
    public AnyM<W,? extends CyclopsCollectable<T>> nestedCollectables() {
        return run.map(ReactiveSeq::fromPublisher);

    }

    @Override
    public <T> FluxT<W,T> unitAnyM(final AnyM<W,Traversable<T>> traversable) {

        return of((AnyM) traversable.map(t -> ReactiveSeq.fromIterable(t)));
    }

    @Override
    public AnyM<W,? extends FoldableTraversable<T>> transformerStream() {

        return run.map(ReactiveSeq::fromPublisher);
    }

    public static <W extends WitnessType<W>,T> FluxT<W,T> emptyList(W witness) {
        return of(witness.<W>adapter().unit(Flux.empty()));
    }

    @Override
    public boolean isSeqPresent() {
        return !run.isEmpty();
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#combine(java.util.function.BiPredicate, java.util.function.BinaryOperator)
     */
    @Override
    public FluxT<W,T> combine(final BiPredicate<? super T, ? super T> predicate, final BinaryOperator<T> op) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.combine(predicate, op);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#cycle(int)
     */
    @Override
    public FluxT<W,T> cycle(final long times) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.cycle(times);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#cycle(cyclops2.function.Monoid, int)
     */
    @Override
    public FluxT<W,T> cycle(final Monoid<T> m, final long times) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.cycle(m, times);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#cycleWhile(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,T> cycleWhile(final Predicate<? super T> predicate) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.cycleWhile(predicate);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#cycleUntil(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,T> cycleUntil(final Predicate<? super T> predicate) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.cycleUntil(predicate);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#zip(java.lang.Iterable, java.util.function.BiFunction)
     */
    @Override
    public <U, R> FluxT<W,R> zip(final Iterable<? extends U> other, final BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (FluxT<W,R>) FoldableTransformerSeq.super.zip(other, zipper);
    }


    @Override
    public <U> FluxT<W,Tuple2<T, U>> zipS(Stream<? extends U> other) {
        return (FluxT) FoldableTransformerSeq.super.zipS(other);
    }

    @Override
    public <U, R> FluxT<W,R> zipS(Stream<? extends U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
        return (FluxT<W,R>) FoldableTransformerSeq.super.zipS(other, zipper);
    }

    /* (non-Javadoc)
         * @see cyclops2.monads.transformers.ListT#zip(java.lang.Iterable)
         */
    @Override
    public <U> FluxT<W,Tuple2<T, U>> zip(final Iterable<? extends U> other) {

        return (FluxT) FoldableTransformerSeq.super.zip(other);
    }



    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#zip3(java.util.reactiveFlux.Flux, java.util.reactiveFlux.Flux)
     */
    @Override
    public <S, U> FluxT<W,Tuple3<T, S, U>> zip3(final Iterable<? extends S> second, final Iterable<? extends U> third) {

        return (FluxT) FoldableTransformerSeq.super.zip3(second, third);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#zip4(java.util.reactiveFlux.Flux, java.util.reactiveFlux.Flux, java.util.reactiveFlux.Flux)
     */
    @Override
    public <T2, T3, T4> FluxT<W,Tuple4<T, T2, T3, T4>> zip4(final Iterable<? extends T2> second, final Iterable<? extends T3> third,
                                                              final Iterable<? extends T4> fourth) {

        return (FluxT) FoldableTransformerSeq.super.zip4(second, third, fourth);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#zipWithIndex()
     */
    @Override
    public FluxT<W,Tuple2<T, Long>> zipWithIndex() {

        return (FluxT<W,Tuple2<T, Long>>) FoldableTransformerSeq.super.zipWithIndex();
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#sliding(int)
     */
    @Override
    public FluxT<W,VectorX<T>> sliding(final int windowSize) {

        return (FluxT<W,VectorX<T>>) FoldableTransformerSeq.super.sliding(windowSize);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#sliding(int, int)
     */
    @Override
    public FluxT<W,VectorX<T>> sliding(final int windowSize, final int increment) {

        return (FluxT<W,VectorX<T>>) FoldableTransformerSeq.super.sliding(windowSize, increment);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#grouped(int, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> FluxT<W,C> grouped(final int size, final Supplier<C> supplier) {

        return (FluxT<W,C>) FoldableTransformerSeq.super.grouped(size, supplier);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#groupedUntil(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,ListX<T>> groupedUntil(final Predicate<? super T> predicate) {

        return (FluxT<W,ListX<T>>) FoldableTransformerSeq.super.groupedUntil(predicate);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#groupedStatefullyUntil(java.util.function.BiPredicate)
     */
    @Override
    public FluxT<W,ListX<T>> groupedStatefullyUntil(final BiPredicate<ListX<? super T>, ? super T> predicate) {

        return (FluxT<W,ListX<T>>) FoldableTransformerSeq.super.groupedStatefullyUntil(predicate);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#groupedWhile(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,ListX<T>> groupedWhile(final Predicate<? super T> predicate) {

        return (FluxT<W,ListX<T>>) FoldableTransformerSeq.super.groupedWhile(predicate);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#groupedWhile(java.util.function.Predicate, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> FluxT<W,C> groupedWhile(final Predicate<? super T> predicate, final Supplier<C> factory) {

        return (FluxT<W,C>) FoldableTransformerSeq.super.groupedWhile(predicate, factory);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#groupedUntil(java.util.function.Predicate, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> FluxT<W,C> groupedUntil(final Predicate<? super T> predicate, final Supplier<C> factory) {

        return (FluxT<W,C>) FoldableTransformerSeq.super.groupedUntil(predicate, factory);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#grouped(int)
     */
    @Override
    public FluxT<W,ListX<T>> grouped(final int groupSize) {

        return (FluxT<W,ListX<T>>) FoldableTransformerSeq.super.grouped(groupSize);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#grouped(java.util.function.Function, java.util.reactiveFlux.Collector)
     */
    @Override
    public <K, A, D> FluxT<W,Tuple2<K, D>> grouped(final Function<? super T, ? extends K> classifier, final Collector<? super T, A, D> downFlux) {

        return (FluxT) FoldableTransformerSeq.super.grouped(classifier, downFlux);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#grouped(java.util.function.Function)
     */
    @Override
    public <K> FluxT<W,Tuple2<K, ReactiveSeq<T>>> grouped(final Function<? super T, ? extends K> classifier) {

        return (FluxT) FoldableTransformerSeq.super.grouped(classifier);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#distinct()
     */
    @Override
    public FluxT<W,T> distinct() {

        return (FluxT<W,T>) FoldableTransformerSeq.super.distinct();
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#scanLeft(cyclops2.function.Monoid)
     */
    @Override
    public FluxT<W,T> scanLeft(final Monoid<T> monoid) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.scanLeft(monoid);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#scanLeft(java.lang.Object, java.util.function.BiFunction)
     */
    @Override
    public <U> FluxT<W,U> scanLeft(final U seed, final BiFunction<? super U, ? super T, ? extends U> function) {

        return (FluxT<W,U>) FoldableTransformerSeq.super.scanLeft(seed, function);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#scanRight(cyclops2.function.Monoid)
     */
    @Override
    public FluxT<W,T> scanRight(final Monoid<T> monoid) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.scanRight(monoid);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#scanRight(java.lang.Object, java.util.function.BiFunction)
     */
    @Override
    public <U> FluxT<W,U> scanRight(final U identity, final BiFunction<? super T, ? super U, ? extends U> combiner) {

        return (FluxT<W,U>) FoldableTransformerSeq.super.scanRight(identity, combiner);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#sorted()
     */
    @Override
    public FluxT<W,T> sorted() {

        return (FluxT<W,T>) FoldableTransformerSeq.super.sorted();
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#sorted(java.util.Comparator)
     */
    @Override
    public FluxT<W,T> sorted(final Comparator<? super T> c) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.sorted(c);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#takeWhile(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,T> takeWhile(final Predicate<? super T> p) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.takeWhile(p);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#dropWhile(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,T> dropWhile(final Predicate<? super T> p) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.dropWhile(p);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#takeUntil(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,T> takeUntil(final Predicate<? super T> p) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.takeUntil(p);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#dropUntil(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,T> dropUntil(final Predicate<? super T> p) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.dropUntil(p);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#dropRight(int)
     */
    @Override
    public FluxT<W,T> dropRight(final int num) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.dropRight(num);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#takeRight(int)
     */
    @Override
    public FluxT<W,T> takeRight(final int num) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.takeRight(num);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#skip(long)
     */
    @Override
    public FluxT<W,T> skip(final long num) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.skip(num);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#skipWhile(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,T> skipWhile(final Predicate<? super T> p) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.skipWhile(p);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#skipUntil(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,T> skipUntil(final Predicate<? super T> p) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.skipUntil(p);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#limit(long)
     */
    @Override
    public FluxT<W,T> limit(final long num) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.limit(num);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#limitWhile(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,T> limitWhile(final Predicate<? super T> p) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.limitWhile(p);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#limitUntil(java.util.function.Predicate)
     */
    @Override
    public FluxT<W,T> limitUntil(final Predicate<? super T> p) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.limitUntil(p);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#intersperse(java.lang.Object)
     */
    @Override
    public FluxT<W,T> intersperse(final T value) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.intersperse(value);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#reverse()
     */
    @Override
    public FluxT<W,T> reverse() {

        return (FluxT<W,T>) FoldableTransformerSeq.super.reverse();
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#shuffle()
     */
    @Override
    public FluxT<W,T> shuffle() {

        return (FluxT<W,T>) FoldableTransformerSeq.super.shuffle();
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#skipLast(int)
     */
    @Override
    public FluxT<W,T> skipLast(final int num) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.skipLast(num);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#limitLast(int)
     */
    @Override
    public FluxT<W,T> limitLast(final int num) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.limitLast(num);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#onEmpty(java.lang.Object)
     */
    @Override
    public FluxT<W,T> onEmpty(final T value) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.onEmpty(value);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#onEmptyGet(java.util.function.Supplier)
     */
    @Override
    public FluxT<W,T> onEmptyGet(final Supplier<? extends T> supplier) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.onEmptyGet(supplier);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#onEmptyThrow(java.util.function.Supplier)
     */
    @Override
    public <X extends Throwable> FluxT<W,T> onEmptyThrow(final Supplier<? extends X> supplier) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.onEmptyThrow(supplier);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#shuffle(java.util.Random)
     */
    @Override
    public FluxT<W,T> shuffle(final Random random) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.shuffle(random);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#slice(long, long)
     */
    @Override
    public FluxT<W,T> slice(final long from, final long to) {

        return (FluxT<W,T>) FoldableTransformerSeq.super.slice(from, to);
    }

    /* (non-Javadoc)
     * @see cyclops2.monads.transformers.values.ListT#sorted(java.util.function.Function)
     */
    @Override
    public <U extends Comparable<? super U>> FluxT<W,T> sorted(final Function<? super T, ? extends U> function) {
        return (FluxT) FoldableTransformerSeq.super.sorted(function);
    }

    @Override
    public int hashCode() {
        return run.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof FluxT) {
            return run.equals(((FluxT) o).run);
        }
        return false;
    }



    public <T2, R1, R2, R3, R> FluxT<W,R> forEach4M(Function<? super T, ? extends FluxT<W,R1>> value1,
                                                      BiFunction<? super T, ? super R1, ? extends FluxT<W,R2>> value2,
                                                      Fn3<? super T, ? super R1, ? super R2, ? extends FluxT<W,R3>> value3,
                                                      Fn4<? super T, ? super R1, ? super R2, ? super R3, ? extends R> yieldingFunction) {
        return this.flatMapT(in->value1.apply(in)
                .flatMapT(in2-> value2.apply(in,in2)
                        .flatMapT(in3->value3.apply(in,in2,in3)
                                .map(in4->yieldingFunction.apply(in,in2,in3,in4)))));

    }
    public <T2, R1, R2, R3, R> FluxT<W,R> forEach4M(Function<? super T, ? extends FluxT<W,R1>> value1,
                                                      BiFunction<? super T, ? super R1, ? extends FluxT<W,R2>> value2,
                                                      Fn3<? super T, ? super R1, ? super R2, ? extends FluxT<W,R3>> value3,
                                                      Fn4<? super T, ? super R1, ? super R2, ? super R3, Boolean> filterFunction,
                                                      Fn4<? super T, ? super R1, ? super R2, ? super R3, ? extends R> yieldingFunction) {
        return this.flatMapT(in->value1.apply(in)
                .flatMapT(in2-> value2.apply(in,in2)
                        .flatMapT(in3->value3.apply(in,in2,in3)
                                .filter(in4->filterFunction.apply(in,in2,in3,in4))
                                .map(in4->yieldingFunction.apply(in,in2,in3,in4)))));

    }

    public <T2, R1, R2, R> FluxT<W,R> forEach3M(Function<? super T, ? extends FluxT<W,R1>> value1,
                                                  BiFunction<? super T, ? super R1, ? extends FluxT<W,R2>> value2,
                                                  Fn3<? super T, ? super R1, ? super R2, ? extends R> yieldingFunction) {

        return this.flatMapT(in->value1.apply(in).flatMapT(in2-> value2.apply(in,in2)
                .map(in3->yieldingFunction.apply(in,in2,in3))));

    }

    public <T2, R1, R2, R> FluxT<W,R> forEach3M(Function<? super T, ? extends FluxT<W,R1>> value1,
                                                  BiFunction<? super T, ? super R1, ? extends FluxT<W,R2>> value2,
                                                  Fn3<? super T, ? super R1, ? super R2, Boolean> filterFunction,
                                                  Fn3<? super T, ? super R1, ? super R2, ? extends R> yieldingFunction) {

        return this.flatMapT(in->value1.apply(in).flatMapT(in2-> value2.apply(in,in2).filter(in3->filterFunction.apply(in,in2,in3))
                .map(in3->yieldingFunction.apply(in,in2,in3))));

    }
    public <R1, R> FluxT<W,R> forEach2M(Function<? super T, ? extends FluxT<W,R1>> value1,
                                          BiFunction<? super T, ? super R1, ? extends R> yieldingFunction) {


        return this.flatMapT(in->value1.apply(in)
                .map(in2->yieldingFunction.apply(in,in2)));
    }

    public <R1, R> FluxT<W,R> forEach2M(Function<? super T, ? extends FluxT<W,R1>> value1,
                                          BiFunction<? super T, ? super R1, Boolean> filterFunction,
                                          BiFunction<? super T, ? super R1, ? extends R> yieldingFunction) {


        return this.flatMapT(in->value1.apply(in)
                .filter(in2->filterFunction.apply(in,in2))
                .map(in2->yieldingFunction.apply(in,in2)));
    }
}