package com.aol.cyclops.reactor.collections.extensions.persistent;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Random;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;
import org.pcollections.PStack;
import org.pcollections.PVector;
import org.reactivestreams.Publisher;

import com.aol.cyclops.Monoid;
import com.aol.cyclops.Reducer;
import com.aol.cyclops.Reducers;
import com.aol.cyclops.control.Matchable.CheckValue1;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.control.Trampoline;
import com.aol.cyclops.data.collections.extensions.persistent.PStackX;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.reactor.Fluxes;
import com.aol.cyclops.reactor.collections.extensions.base.AbstractFluentCollectionX;
import com.aol.cyclops.reactor.collections.extensions.base.LazyFluentCollection;
import com.aol.cyclops.reactor.collections.extensions.base.NativePlusLoop;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Wither;
import reactor.core.publisher.Flux;

/**
 * An extended Persistent List type {@see java.util.List}
 * This makes use of PStack (@see org.pcollections.PStack) from PCollectons. PStack is a persistent analogue of  the 
 * imperative LinkedList type.
 * Extended List operations execute lazily (compared with @see com.aol.cyclops.data.collections.extensions.persistent.PStackX)  e.g.
 * <pre>
 * {@code 
 *    LazyPStackX<Integer> q = LazyPStackX.of(1,2,3)
 *                                      .map(i->i*2);
 * }
 * </pre>
 * The map operation above is not executed immediately. It will only be executed when (if) the data inside the
 * PStack is accessed. This allows lazy operations to be chained and executed more efficiently e.g.
 * 
 * <pre>
 * {@code 
 *    LazyPStackX<Integer> q = LazyPStackX.of(1,2,3)
 *                                      .map(i->i*2);
 *                                      .filter(i->i<5);
 * }
 * </pre>
 * 
 * The operation above is more efficient than the equivalent operation with a PStackX.
 * 
 * @author johnmcclean
 *
 * @param <T> the type of elements held in this collection
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LazyPStackX<T> extends AbstractFluentCollectionX<T>implements PStackX<T> {
    private final LazyFluentCollection<T, PStack<T>> lazy;
    @Getter
    private final Reducer<PStack<T>> collector;
    @Wither
    @Getter
    private final boolean efficientOps;

    
    @Override
    public LazyPStackX<T> plusLoop(int max, IntFunction<T> value){
        PStack<T> list = lazy.get();
        if(list instanceof NativePlusLoop){
            return (LazyPStackX<T>) ((NativePlusLoop)list).plusLoop(max, value);
        }else{
            return (LazyPStackX<T>) super.plusLoop(max, value);
        }
    }
    @Override
    public LazyPStackX<T> plusLoop(Supplier<Optional<T>> supplier){
        PStack<T> list = lazy.get();
        if(list instanceof NativePlusLoop){
            return (LazyPStackX<T>) ((NativePlusLoop)list).plusLoop(supplier);
        }else{
            return (LazyPStackX<T>) super.plusLoop(supplier);
        }
    }
    public LazyPStackX<T> efficientOpsOn() {
        return this.withEfficientOps(true);
    }

    public LazyPStackX<T> efficientOpsOff() {
        return this.withEfficientOps(false);
    }
    public static <T> LazyPStackX<T> fromPStack(PStack<T> list,Reducer<PStack<T>> collector){
        return new LazyPStackX<T>(list,collector);
    }
    /**
     * Create a LazyPStackX from a Stream
     * 
     * @param stream to construct a LazyQueueX from
     * @return LazyPStackX
     */
    public static <T> LazyPStackX<T> fromStreamS(Stream<T> stream) {
        return new LazyPStackX<T>(
                                  Flux.from(ReactiveSeq.fromStream(stream)));
    }

    /**
     * Create a LazyPStackX that contains the Integers between start and end
     * 
     * @param start
     *            Number of range to start from
     * @param end
     *            Number for range to end at
     * @return Range ListX
     */
    public static LazyPStackX<Integer> range(int start, int end) {
        return fromStreamS(ReactiveSeq.range(start, end));
    }

    /**
     * Create a LazyPStackX that contains the Longs between start and end
     * 
     * @param start
     *            Number of range to start from
     * @param end
     *            Number for range to end at
     * @return Range ListX
     */
    public static LazyPStackX<Long> rangeLong(long start, long end) {
        return fromStreamS(ReactiveSeq.rangeLong(start, end));
    }

    /**
     * Unfold a function into a ListX
     * 
     * <pre>
     * {@code 
     *  LazyPStackX.unfold(1,i->i<=6 ? Optional.of(Tuple.tuple(i,i+1)) : Optional.empty());
     * 
     * //(1,2,3,4,5)
     * 
     * }</pre>
     * 
     * @param seed Initial value 
     * @param unfolder Iteratively applied function, terminated by an empty Optional
     * @return ListX generated by unfolder function
     */
    public static <U, T> LazyPStackX<T> unfold(U seed, Function<? super U, Optional<Tuple2<T, U>>> unfolder) {
        return fromStreamS(ReactiveSeq.unfold(seed, unfolder));
    }

    /**
     * Generate a LazyPStackX from the provided Supplier up to the provided limit number of times
     * 
     * @param limit Max number of elements to generate
     * @param s Supplier to generate ListX elements
     * @return ListX generated from the provided Supplier
     */
    public static <T> LazyPStackX<T> generate(long limit, Supplier<T> s) {

        return fromStreamS(ReactiveSeq.generate(s)
                                      .limit(limit));
    }

    /**
     * Create a LazyPStackX by iterative application of a function to an initial element up to the supplied limit number of times
     * 
     * @param limit Max number of elements to generate
     * @param seed Initial element
     * @param f Iteratively applied to each element to generate the next element
     * @return ListX generated by iterative application
     */
    public static <T> LazyPStackX<T> iterate(long limit, final T seed, final UnaryOperator<T> f) {
        return fromStreamS(ReactiveSeq.iterate(seed, f)
                                      .limit(limit));
    }

    /**
     * @return A collector that generates a LazyPStackX
     */
    public static <T> Collector<T, ?, LazyPStackX<T>> lazyListXCollector() {
        return Collectors.toCollection(() -> LazyPStackX.of());
    }

    /**
     * @return An empty LazyPStackX
     */
    public static <T> LazyPStackX<T> empty() {
        return fromIterable((List<T>) ListX.<T> defaultCollector()
                                           .supplier()
                                           .get());
    }

    /**
     * Create a LazyPStackX from the specified values
     * <pre>
     * {@code 
     *     ListX<Integer> lazy = LazyPStackX.of(1,2,3,4,5);
     *     
     *     //lazily map List
     *     ListX<String> mapped = lazy.map(i->"mapped " +i); 
     *     
     *     String value = mapped.get(0); //transformation triggered now
     * }
     * </pre>
     * 
     * @param values To populate LazyPStackX with
     * @return LazyPStackX
     */
    @SafeVarargs
    public static <T> LazyPStackX<T> of(T... values) {
        List<T> res = (List<T>) ListX.<T> defaultCollector()
                                     .supplier()
                                     .get();
        for (T v : values)
            res.add(v);
        return fromIterable(res);
    }

    /**
     * Construct a LazyPStackX with a single value
     * <pre>
     * {@code 
     *    ListX<Integer> lazy = LazyPStackX.singleton(5);
     *    
     * }
     * </pre>
     * 
     * 
     * @param value To populate LazyPStackX with
     * @return LazyPStackX with a single value
     */
    public static <T> LazyPStackX<T> singleton(T value) {
        return LazyPStackX.<T> of(value);
    }

    /**
     * Construct a LazyPStackX from an Publisher
     * 
     * @param publisher
     *            to construct LazyPStackX from
     * @return ListX
     */
    public static <T> LazyPStackX<T> fromPublisher(Publisher<? extends T> publisher) {
        return fromStreamS(ReactiveSeq.fromPublisher((Publisher<T>) publisher));
    }

    /**
     * Construct LazyPStackX from an Iterable
     * 
     * @param it to construct LazyPStackX from
     * @return LazyPStackX from Iterable
     */
    public static <T> LazyPStackX<T> fromIterable(Iterable<T> it) {
        return fromIterable(Reducers.toPStack(), it);
    }

    /**
     * Construct a LazyPStackX from an Iterable, using the specified Collector.
     * 
     * @param collector To generate Lists from, this can be used to create mutable vs immutable Lists (for example), or control List type (ArrayList, LinkedList)
     * @param it Iterable to construct LazyPStackX from
     * @return Newly constructed LazyPStackX
     */
    public static <T> LazyPStackX<T> fromIterable(Reducer<PStack<T>> collector, Iterable<T> it) {
        if (it instanceof LazyPStackX)
            return (LazyPStackX<T>) it;

        if (it instanceof PStack)
            return new LazyPStackX<T>(
                                      (PStack<T>) it, collector);

        return new LazyPStackX<T>(
                                  Flux.fromIterable(it), collector);
    }

    private LazyPStackX(PStack<T> list, Reducer<PStack<T>> collector) {
        this.efficientOps = true;
        this.lazy = new PersistentLazyCollection<T, PStack<T>>(
                                                               list, null, collector);
        this.collector = collector;
    }

    private LazyPStackX(boolean efficientOps, PStack<T> list, Reducer<PStack<T>> collector) {
        this.efficientOps = efficientOps;
        this.lazy = new PersistentLazyCollection<T, PStack<T>>(
                                                               list, null, collector);
        this.collector = collector;
    }

    private LazyPStackX(PStack<T> list) {
        this.efficientOps = true;
        this.collector = Reducers.toPStack();
        this.lazy = new PersistentLazyCollection<T, PStack<T>>(
                                                               list, null, Reducers.toPStack());
    }
   

    public LazyPStackX(Flux<T> stream, Reducer<PStack<T>> collector) {
        this.efficientOps = true;
        this.collector = collector;
        this.lazy = new PersistentLazyCollection<>(
                                                   null, stream, Reducers.toPStack());
    }

    private LazyPStackX(Flux<T> stream) {
        this.efficientOps = true;
        this.collector = Reducers.toPStack();
        this.lazy = new PersistentLazyCollection<>(
                                                   null, stream, collector);
    }

    private LazyPStackX() {
        this.efficientOps = true;
        this.collector = Reducers.toPStack();
        this.lazy = new PersistentLazyCollection<>(
                                                   (PStack) this.collector.zero(), null, collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Iterable#forEach(java.util.function.Consumer)
     */
    @Override
    public void forEach(Consumer<? super T> action) {
        getStack().forEach(action);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Iterable#iterator()
     */
    @Override
    public Iterator<T> iterator() {
        return getStack().iterator();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#size()
     */
    @Override
    public int size() {
        return getStack().size();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#contains(java.lang.Object)
     */
    @Override
    public boolean contains(Object e) {
        return getStack().contains(e);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        return getStack().equals(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#isEmpty()
     */
    @Override
    public boolean isEmpty() {
        return getStack().isEmpty();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getStack().hashCode();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#toArray()
     */
    @Override
    public Object[] toArray() {
        return getStack().toArray();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#removeAll(java.util.Collection)
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        return getStack().removeAll(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#toArray(java.lang.Object[])
     */
    @Override
    public <T> T[] toArray(T[] a) {
        return getStack().toArray(a);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#add(java.lang.Object)
     */
    @Override
    public boolean add(T e) {
        return getStack().add(e);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#remove(java.lang.Object)
     */
    @Override
    public boolean remove(Object o) {
        return getStack().remove(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#containsAll(java.util.Collection)
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        return getStack().containsAll(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#addAll(java.util.Collection)
     */
    @Override
    public boolean addAll(Collection<? extends T> c) {
        return getStack().addAll(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#retainAll(java.util.Collection)
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        return getStack().retainAll(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#clear()
     */
    @Override
    public void clear() {
        getStack().clear();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getStack().toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jooq.lambda.Collectable#collect(java.util.stream.Collector)
     */
    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return stream().collect(collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jooq.lambda.Collectable#count()
     */
    @Override
    public long count() {
        return this.size();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#addAll(int, java.util.Collection)
     */
    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        return getStack().addAll(index, c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#replaceAll(java.util.function.UnaryOperator)
     */
    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        getStack().replaceAll(operator);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#removeIf(java.util.function.Predicate)
     */
    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        return getStack().removeIf(filter);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#sort(java.util.Comparator)
     */
    @Override
    public void sort(Comparator<? super T> c) {
        getStack().sort(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#get(int)
     */
    @Override
    public T get(int index) {
        return getStack().get(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#set(int, java.lang.Object)
     */
    @Override
    public T set(int index, T element) {
        return getStack().set(index, element);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#add(int, java.lang.Object)
     */
    @Override
    public void add(int index, T element) {
        getStack().add(index, element);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#remove(int)
     */
    @Override
    public T remove(int index) {
        return getStack().remove(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Collection#parallelStream()
     */
    @Override
    public Stream<T> parallelStream() {
        return getStack().parallelStream();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#indexOf(java.lang.Object)
     */
    @Override
    public int indexOf(Object o) {
        // return
        // stream().zipWithIndex().filter(t->Objects.equals(t.v1,o)).findFirst().get().v2.intValue();
        return getStack().indexOf(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#lastIndexOf(java.lang.Object)
     */
    @Override
    public int lastIndexOf(Object o) {
        return getStack().lastIndexOf(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#listIterator()
     */
    @Override
    public ListIterator<T> listIterator() {
        return getStack().listIterator();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#listIterator(int)
     */
    @Override
    public ListIterator<T> listIterator(int index) {
        return getStack().listIterator(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.data.collections.extensions.standard.ListX#subList(int,
     * int)
     */
    @Override
    public LazyPStackX<T> subList(int fromIndex, int toIndex) {
        return new LazyPStackX<T>(
                                  getStack().subList(fromIndex, toIndex), getCollector());
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Iterable#spliterator()
     */
    @Override
    public Spliterator<T> spliterator() {
        return getStack().spliterator();
    }

    /**
     * @return PStack
     */
    private PStack<T> getStack() {
        return lazy.get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#stream(reactor.core.publisher.Flux)
     */
    @Override
    public <X> LazyPStackX<X> stream(Flux<X> stream) {
        return new LazyPStackX<X>(
                                  stream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#flux()
     */
    @Override
    public Flux<T> flux() {
        return lazy.flux();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#combine(java.util.function.BiPredicate,
     * java.util.function.BinaryOperator)
     */
    @Override
    public LazyPStackX<T> combine(BiPredicate<? super T, ? super T> predicate, BinaryOperator<T> op) {

        return (LazyPStackX<T>) super.combine(predicate, op);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#reverse()
     */
    @Override
    public LazyPStackX<T> reverse() {

        return (LazyPStackX<T>) super.reverse();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#filter(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> filter(Predicate<? super T> pred) {

        return (LazyPStackX<T>) super.filter(pred);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#map(java.util.function.Function)
     */
    @Override
    public <R> LazyPStackX<R> map(Function<? super T, ? extends R> mapper) {

        return (LazyPStackX<R>) super.map(mapper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#flatMap(java.util.function.Function)
     */
    @Override
    public <R> LazyPStackX<R> flatMap(Function<? super T, ? extends Iterable<? extends R>> mapper) {
        return (LazyPStackX<R>) super.flatMap(mapper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#limit(long)
     */
    @Override
    public LazyPStackX<T> limit(long num) {
        return (LazyPStackX<T>) super.limit(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#skip(long)
     */
    @Override
    public LazyPStackX<T> skip(long num) {
        return (LazyPStackX<T>) super.skip(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#takeRight(int)
     */
    @Override
    public LazyPStackX<T> takeRight(int num) {
        return (LazyPStackX<T>) super.takeRight(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#dropRight(int)
     */
    @Override
    public LazyPStackX<T> dropRight(int num) {
        return (LazyPStackX<T>) super.dropRight(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#takeWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> takeWhile(Predicate<? super T> p) {
        return (LazyPStackX<T>) super.takeWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#dropWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> dropWhile(Predicate<? super T> p) {
        return (LazyPStackX<T>) super.dropWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#takeUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> takeUntil(Predicate<? super T> p) {
        return (LazyPStackX<T>) super.takeUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#dropUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> dropUntil(Predicate<? super T> p) {
        return (LazyPStackX<T>) super.dropUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#trampoline(java.util.function.Function)
     */
    @Override
    public <R> LazyPStackX<R> trampoline(Function<? super T, ? extends Trampoline<? extends R>> mapper) {
        return (LazyPStackX<R>) super.trampoline(mapper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#slice(long, long)
     */
    @Override
    public LazyPStackX<T> slice(long from, long to) {
        return (LazyPStackX<T>) super.slice(from, to);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#grouped(int)
     */
    @Override
    public LazyPStackX<ListX<T>> grouped(int groupSize) {

        return (LazyPStackX<ListX<T>>) super.grouped(groupSize);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#grouped(java.util.function.Function,
     * java.util.stream.Collector)
     */
    @Override
    public <K, A, D> LazyPStackX<Tuple2<K, D>> grouped(Function<? super T, ? extends K> classifier,
            Collector<? super T, A, D> downstream) {

        return (LazyPStackX) super.grouped(classifier, downstream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#grouped(java.util.function.Function)
     */
    @Override
    public <K> LazyPStackX<Tuple2<K, Seq<T>>> grouped(Function<? super T, ? extends K> classifier) {

        return (LazyPStackX) super.grouped(classifier);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(java.lang.Iterable)
     */
    @Override
    public <U> LazyPStackX<Tuple2<T, U>> zip(Iterable<? extends U> other) {

        return (LazyPStackX) super.zip(other);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(java.lang.Iterable,
     * java.util.function.BiFunction)
     */
    @Override
    public <U, R> LazyPStackX<R> zip(Iterable<? extends U> other,
            BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (LazyPStackX<R>) super.zip(other, zipper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#sliding(int)
     */
    @Override
    public LazyPStackX<ListX<T>> sliding(int windowSize) {

        return (LazyPStackX<ListX<T>>) super.sliding(windowSize);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#sliding(int, int)
     */
    @Override
    public LazyPStackX<ListX<T>> sliding(int windowSize, int increment) {

        return (LazyPStackX<ListX<T>>) super.sliding(windowSize, increment);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#scanLeft(com.aol.cyclops.Monoid)
     */
    @Override
    public LazyPStackX<T> scanLeft(Monoid<T> monoid) {

        return (LazyPStackX<T>) super.scanLeft(monoid);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#scanLeft(java.lang.Object,
     * java.util.function.BiFunction)
     */
    @Override
    public <U> LazyPStackX<U> scanLeft(U seed, BiFunction<? super U, ? super T, ? extends U> function) {

        return (LazyPStackX<U>) super.scanLeft(seed, function);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#scanRight(com.aol.cyclops.Monoid)
     */
    @Override
    public LazyPStackX<T> scanRight(Monoid<T> monoid) {

        return (LazyPStackX<T>) super.scanRight(monoid);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#scanRight(java.lang.Object,
     * java.util.function.BiFunction)
     */
    @Override
    public <U> LazyPStackX<U> scanRight(U identity, BiFunction<? super T, ? super U, ? extends U> combiner) {

        return (LazyPStackX<U>) super.scanRight(identity, combiner);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#sorted(java.util.function.Function)
     */
    @Override
    public <U extends Comparable<? super U>> LazyPStackX<T> sorted(Function<? super T, ? extends U> function) {

        return (LazyPStackX<T>) super.sorted(function);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#plusLazy(java.lang.Object)
     */
    @Override
    public LazyPStackX<T> plusLazy(T e) {

        return (LazyPStackX<T>) super.plusLazy(e);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#plusAllLazy(java.util.Collection)
     */
    @Override
    public LazyPStackX<T> plusAllLazy(Collection<? extends T> list) {

        return (LazyPStackX<T>) super.plusAllLazy(list);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#minusLazy(java.lang.Object)
     */
    @Override
    public LazyPStackX<T> minusLazy(Object e) {

        return (LazyPStackX<T>) super.minusLazy(e);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#minusAllLazy(java.util.Collection)
     */
    @Override
    public LazyPStackX<T> minusAllLazy(Collection<?> list) {

        return (LazyPStackX<T>) super.minusAllLazy(list);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#cycle(int)
     */
    @Override
    public LazyPStackX<T> cycle(int times) {

        return (LazyPStackX<T>) super.cycle(times);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#cycle(com.aol.cyclops.Monoid, int)
     */
    @Override
    public LazyPStackX<T> cycle(Monoid<T> m, int times) {

        return (LazyPStackX<T>) super.cycle(m, times);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#cycleWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> cycleWhile(Predicate<? super T> predicate) {

        return (LazyPStackX<T>) super.cycleWhile(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#cycleUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> cycleUntil(Predicate<? super T> predicate) {

        return (LazyPStackX<T>) super.cycleUntil(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(org.jooq.lambda.Seq)
     */
    @Override
    public <U> LazyPStackX<Tuple2<T, U>> zip(Seq<? extends U> other) {

        return (LazyPStackX) super.zip(other);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip3(java.util.stream.Stream,
     * java.util.stream.Stream)
     */
    @Override
    public <S, U> LazyPStackX<Tuple3<T, S, U>> zip3(Stream<? extends S> second, Stream<? extends U> third) {

        return (LazyPStackX) super.zip3(second, third);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip4(java.util.stream.Stream,
     * java.util.stream.Stream, java.util.stream.Stream)
     */
    @Override
    public <T2, T3, T4> LazyPStackX<Tuple4<T, T2, T3, T4>> zip4(Stream<? extends T2> second, Stream<? extends T3> third,
            Stream<? extends T4> fourth) {

        return (LazyPStackX) super.zip4(second, third, fourth);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zipWithIndex()
     */
    @Override
    public LazyPStackX<Tuple2<T, Long>> zipWithIndex() {

        return (LazyPStackX<Tuple2<T, Long>>) super.zipWithIndex();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#distinct()
     */
    @Override
    public LazyPStackX<T> distinct() {

        return (LazyPStackX<T>) super.distinct();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#sorted()
     */
    @Override
    public LazyPStackX<T> sorted() {

        return (LazyPStackX<T>) super.sorted();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#sorted(java.util.Comparator)
     */
    @Override
    public LazyPStackX<T> sorted(Comparator<? super T> c) {

        return (LazyPStackX<T>) super.sorted(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#skipWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> skipWhile(Predicate<? super T> p) {

        return (LazyPStackX<T>) super.skipWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#skipUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> skipUntil(Predicate<? super T> p) {

        return (LazyPStackX<T>) super.skipUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#limitWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> limitWhile(Predicate<? super T> p) {

        return (LazyPStackX<T>) super.limitWhile(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#limitUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> limitUntil(Predicate<? super T> p) {

        return (LazyPStackX<T>) super.limitUntil(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#intersperse(java.lang.Object)
     */
    @Override
    public LazyPStackX<T> intersperse(T value) {

        return (LazyPStackX<T>) super.intersperse(value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#shuffle()
     */
    @Override
    public LazyPStackX<T> shuffle() {

        return (LazyPStackX<T>) super.shuffle();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#skipLast(int)
     */
    @Override
    public LazyPStackX<T> skipLast(int num) {

        return (LazyPStackX<T>) super.skipLast(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#limitLast(int)
     */
    @Override
    public LazyPStackX<T> limitLast(int num) {

        return (LazyPStackX<T>) super.limitLast(num);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#onEmpty(java.lang.Object)
     */
    @Override
    public LazyPStackX<T> onEmpty(T value) {

        return (LazyPStackX<T>) super.onEmpty(value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#onEmptyGet(java.util.function.Supplier)
     */
    @Override
    public LazyPStackX<T> onEmptyGet(Supplier<? extends T> supplier) {

        return (LazyPStackX<T>) super.onEmptyGet(supplier);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#onEmptyThrow(java.util.function.Supplier)
     */
    @Override
    public <X extends Throwable> LazyPStackX<T> onEmptyThrow(Supplier<? extends X> supplier) {

        return (LazyPStackX<T>) super.onEmptyThrow(supplier);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#shuffle(java.util.Random)
     */
    @Override
    public LazyPStackX<T> shuffle(Random random) {

        return (LazyPStackX<T>) super.shuffle(random);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#ofType(java.lang.Class)
     */
    @Override
    public <U> LazyPStackX<U> ofType(Class<? extends U> type) {

        return (LazyPStackX<U>) super.ofType(type);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#filterNot(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<T> filterNot(Predicate<? super T> fn) {

        return (LazyPStackX<T>) super.filterNot(fn);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#notNull()
     */
    @Override
    public LazyPStackX<T> notNull() {

        return (LazyPStackX<T>) super.notNull();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#removeAll(java.util.stream.Stream)
     */
    @Override
    public LazyPStackX<T> removeAll(Stream<? extends T> stream) {

        return (LazyPStackX<T>) (super.removeAll(stream));
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#removeAll(org.jooq.lambda.Seq)
     */
    @Override
    public LazyPStackX<T> removeAll(Seq<? extends T> stream) {

        return (LazyPStackX<T>) super.removeAll(stream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#removeAll(java.lang.Iterable)
     */
    @Override
    public LazyPStackX<T> removeAll(Iterable<? extends T> it) {

        return (LazyPStackX<T>) super.removeAll(it);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#removeAll(java.lang.Object[])
     */
    @Override
    public LazyPStackX<T> removeAll(T... values) {

        return (LazyPStackX<T>) super.removeAll(values);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#retainAll(java.lang.Iterable)
     */
    @Override
    public LazyPStackX<T> retainAll(Iterable<? extends T> it) {

        return (LazyPStackX<T>) super.retainAll(it);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#retainAll(java.util.stream.Stream)
     */
    @Override
    public LazyPStackX<T> retainAll(Stream<? extends T> stream) {

        return (LazyPStackX<T>) super.retainAll(stream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#retainAll(org.jooq.lambda.Seq)
     */
    @Override
    public LazyPStackX<T> retainAll(Seq<? extends T> stream) {

        return (LazyPStackX<T>) super.retainAll(stream);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#retainAll(java.lang.Object[])
     */
    @Override
    public LazyPStackX<T> retainAll(T... values) {

        return (LazyPStackX<T>) super.retainAll(values);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#cast(java.lang.Class)
     */
    @Override
    public <U> LazyPStackX<U> cast(Class<? extends U> type) {

        return (LazyPStackX<U>) super.cast(type);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#patternMatch(java.util.function.Function,
     * java.util.function.Supplier)
     */
    @Override
    public <R> LazyPStackX<R> patternMatch(Function<CheckValue1<T, R>, CheckValue1<T, R>> case1,
            Supplier<? extends R> otherwise) {

        return (LazyPStackX<R>) super.patternMatch(case1, otherwise);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#permutations()
     */
    @Override
    public LazyPStackX<ReactiveSeq<T>> permutations() {

        return (LazyPStackX<ReactiveSeq<T>>) super.permutations();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#combinations(int)
     */
    @Override
    public LazyPStackX<ReactiveSeq<T>> combinations(int size) {

        return (LazyPStackX<ReactiveSeq<T>>) super.combinations(size);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#combinations()
     */
    @Override
    public LazyPStackX<ReactiveSeq<T>> combinations() {

        return (LazyPStackX<ReactiveSeq<T>>) super.combinations();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#grouped(int, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> LazyPStackX<C> grouped(int size, Supplier<C> supplier) {

        return (LazyPStackX<C>) super.grouped(size, supplier);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#groupedUntil(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<ListX<T>> groupedUntil(Predicate<? super T> predicate) {

        return (LazyPStackX<ListX<T>>) super.groupedUntil(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#groupedWhile(java.util.function.Predicate)
     */
    @Override
    public LazyPStackX<ListX<T>> groupedWhile(Predicate<? super T> predicate) {

        return (LazyPStackX<ListX<T>>) super.groupedWhile(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#groupedWhile(java.util.function.Predicate,
     * java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> LazyPStackX<C> groupedWhile(Predicate<? super T> predicate,
            Supplier<C> factory) {

        return (LazyPStackX<C>) super.groupedWhile(predicate, factory);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#groupedUntil(java.util.function.Predicate,
     * java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> LazyPStackX<C> groupedUntil(Predicate<? super T> predicate,
            Supplier<C> factory) {

        return (LazyPStackX<C>) super.groupedUntil(predicate, factory);
    }

    /** PStackX methods **/

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.data.collections.extensions.standard.ListX#with(int,
     * java.lang.Object)
     */
    public LazyPStackX<T> with(int i, T element) {
        return  new LazyPStackX<T>(
                efficientOps, getStack().with(i,element), this.collector);//stream(Fluxes.insertAt(Fluxes.deleteBetween(flux(), i, i + 1), i, element));
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#groupedStatefullyUntil(java.util.function.
     * BiPredicate)
     */
    @Override
    public LazyPStackX<ListX<T>> groupedStatefullyUntil(BiPredicate<ListX<? super T>, ? super T> predicate) {

        return (LazyPStackX<ListX<T>>) super.groupedStatefullyUntil(predicate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#peek(java.util.function.Consumer)
     */
    @Override
    public LazyPStackX<T> peek(Consumer<? super T> c) {

        return (LazyPStackX) super.peek(c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(org.jooq.lambda.Seq,
     * java.util.function.BiFunction)
     */
    @Override
    public <U, R> LazyPStackX<R> zip(Seq<? extends U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (LazyPStackX<R>) super.zip(other, zipper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(java.util.stream.Stream,
     * java.util.function.BiFunction)
     */
    @Override
    public <U, R> LazyPStackX<R> zip(Stream<? extends U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (LazyPStackX<R>) super.zip(other, zipper);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(java.util.stream.Stream)
     */
    @Override
    public <U> LazyPStackX<Tuple2<T, U>> zip(Stream<? extends U> other) {

        return (LazyPStackX) super.zip(other);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.reactor.collections.extensions.base.
     * AbstractFluentCollectionX#zip(java.util.function.BiFunction,
     * org.reactivestreams.Publisher)
     */
    @Override
    public <T2, R> LazyPStackX<R> zip(BiFunction<? super T, ? super T2, ? extends R> fn,
            Publisher<? extends T2> publisher) {

        return (LazyPStackX<R>) super.zip(fn, publisher);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.aol.cyclops.data.collections.extensions.standard.ListX#onEmptySwitch(
     * java.util.function.Supplier)
     */
    @Override
    public LazyPStackX<T> onEmptySwitch(Supplier<? extends PStack<T>> supplier) {
        return stream(Fluxes.onEmptySwitch(flux(), () -> Flux.fromIterable(supplier.get())));

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PStack#subList(int)
     */
    @Override
    public LazyPStackX<T> subList(int start) {
        return stream(flux().take(start));
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.aol.cyclops.data.collections.extensions.standard.ListX#unit(
     * Collection)
     */
    @Override
    public <R> LazyPStackX<R> unit(Collection<R> col) {
        if (isEfficientOps())
            return fromIterable(col);
        return fromIterable(col).efficientOpsOff();
    }

    @Override
    public <R> LazyPStackX<R> unit(R value) {
        return singleton(value);
    }

    @Override
    public <R> LazyPStackX<R> unitIterator(Iterator<R> it) {
        return fromIterable(() -> it);
    }

    @Override
    public <R> LazyPStackX<R> emptyUnit() {
        if (isEfficientOps())
            return empty();
        return LazyPStackX.<R> empty()
                          .efficientOpsOff();
    }

    /**
     * @return This converted to PStack
     */
    public LazyPStackX<T> toPStack() {
        return this;
    }

    @Override
    public LazyPStackX<T> plusInOrder(T e) {
        if (isEfficientOps())
            return plus(e);
        return plus(size(), e);
    }

    @Override
    public ReactiveSeq<T> stream() {

        return ReactiveSeq.fromIterable(this);
    }

    @Override
    public <X> LazyPStackX<X> from(Collection<X> col) {
        if (isEfficientOps())
            return fromIterable(col);
        return fromIterable(col).efficientOpsOff();
    }

    @Override
    public <T> Reducer<PStack<T>> monoid() {
        if (isEfficientOps())
            return Reducers.toPStackReversed();
        return Reducers.toPStack();

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.MapPSet#plus(java.lang.Object)
     */
    public LazyPStackX<T> plus(T e) {
        return new LazyPStackX<T>(
                                  efficientOps, getStack().plus(e), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PStack#plus(int, java.lang.Object)
     */
    public LazyPStackX<T> plus(int i, T e) {
        return new LazyPStackX<T>(
                                  efficientOps, getStack().plus(i, e), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.MapPSet#minus(java.lang.Object)
     */
    public LazyPStackX<T> minus(Object e) {
        return new LazyPStackX<T>(
                                  efficientOps, getStack().minus(e), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.MapPSet#plusAll(java.util.Collection)
     */
    public LazyPStackX<T> plusAll(Collection<? extends T> list) {
        return new LazyPStackX<T>(
                                  efficientOps, getStack().plusAll(list), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PStack#plusAll(int, java.util.Collection)
     */
    public LazyPStackX<T> plusAll(int i, Collection<? extends T> list) {
        return new LazyPStackX<T>(
                                  efficientOps, getStack().plusAll(i, list), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.MapPSet#minusAll(java.util.Collection)
     */
    public LazyPStackX<T> minusAll(Collection<?> list) {
        return new LazyPStackX<T>(
                                  efficientOps, getStack().minusAll(list), this.collector);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.pcollections.PStack#minus(int)
     */
    public LazyPStackX<T> minus(int i) {
        return new LazyPStackX<T>(
                                  efficientOps, getStack().minus(i), this.collector);
    }
    /* (non-Javadoc)
     * @see com.aol.cyclops.reactor.collections.extensions.base.LazyFluentCollectionX#materialize()
     */
    @Override
    public LazyPStackX<T> materialize() {
       this.lazy.get();
       return this;
    }
}
