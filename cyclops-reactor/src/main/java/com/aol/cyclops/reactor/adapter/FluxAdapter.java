package com.aol.cyclops.reactor.adapter;

import com.aol.cyclops.reactor.Fluxs;
import com.aol.cyclops.reactor.ReactorWitness;
import com.aol.cyclops.reactor.ReactorWitness.flux;
import com.aol.cyclops2.types.extensability.AbstractFunctionalAdapter;
import cyclops.monads.AnyM;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.function.Function;
import java.util.function.Predicate;

import static cyclops.monads.AnyM.fromStream;


@AllArgsConstructor
public class FluxAdapter extends AbstractFunctionalAdapter<ReactorWitness.flux> {





    @Override
    public <T> Iterable<T> toIterable(AnyM<flux, T> t) {
        return ()->stream(t).toIterable().iterator();
    }

    @Override
    public <T, R> AnyM<flux, R> ap(AnyM<flux,? extends Function<? super T,? extends R>> fn, AnyM<flux, T> apply) {
        Flux<T> f = stream(apply);
        Flux<? extends Function<? super T, ? extends R>> fnF = stream(fn);
        Flux<R> res = fnF.zipWith(f, (a, b) -> a.apply(b));
        return Fluxs.anyM(res);

    }

    @Override
    public <T> AnyM<flux, T> filter(AnyM<flux, T> t, Predicate<? super T> fn) {
        return Fluxs.anyM(stream(t).filter(fn));
    }

    <T> Flux<T> stream(AnyM<flux,T> anyM){
        return anyM.unwrap();
    }

    @Override
    public <T> AnyM<flux, T> empty() {
        return Fluxs.anyM(Flux.empty());
    }



    @Override
    public <T, R> AnyM<flux, R> flatMap(AnyM<flux, T> t,
                                     Function<? super T, ? extends AnyM<flux, ? extends R>> fn) {
        return Fluxs.anyM(stream(t).flatMap(fn.andThen(a->stream(a))));

    }

    @Override
    public <T> AnyM<flux, T> unitIterable(Iterable<T> it)  {
        return Fluxs.anyM(Flux.fromIterable(it));
    }

    @Override
    public <T> AnyM<flux, T> unit(T o) {
        return Fluxs.anyM(Flux.just(o));
    }



}