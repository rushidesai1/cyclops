package com.aol.cyclops.javaslang.hkt;

import java.util.Optional;

import com.aol.cyclops.control.Eval;
import com.aol.cyclops.hkt.alias.Higher;
import com.aol.cyclops.hkt.jdk.OptionalType;
import com.aol.cyclops.javaslang.FromCyclopsReact;

import javaslang.collection.Iterator;
import javaslang.control.Option;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

/**
 * Simulates Higher Kinded Types for Option's
 * 
 * OptionType is a Option and a Higher Kinded Type (OptionType.µ,T)
 * 
 * @author johnmcclean
 *
 * @param <T> Data type stored within the Option
 */

public interface OptionType<T> extends Higher<OptionType.µ, T>, Option<T> {
    /**
     * Witness type
     * 
     * @author johnmcclean
     *
     */
    public static class µ {
    }
    
    /**
     * @return Get the empty Option (single instance)
     */
    @SuppressWarnings("unchecked")
    static <T> OptionType<T> none() {
        return widen(Option.none());
    }
    /**
     *  Construct a OptionType  that contains a single value extracted from the supplied Iterable
     * <pre>
     * {@code 
     *   ReactiveSeq<Integer> stream =  ReactiveSeq.of(1,2,3);
        
         OptionType<Integer> maybe = OptionType.fromIterable(stream);
        
        //Option[1]
     * 
     * }
     * </pre> 
     * @param iterable Iterable  to extract value from
     * @return Option populated with first value from Iterable (Option.empty if Publisher empty)
     */
    static <T> OptionType<T> fromIterable(final Iterable<T> iterable) {
        return widen(FromCyclopsReact.option(Eval.fromIterable(iterable)));
    }

    /**
     * Construct an equivalent Option from the Supplied Optional
     * <pre>
     * {@code 
     *   OptionType<Integer> some = OptionType.ofOptional(Optional.of(10));
     *   //Option[10], Some[10]
     *  
     *   OptionType<Integer> none = OptionType.ofOptional(Optional.empty());
     *   //Option.empty, None[]
     * }
     * </pre>
     * 
     * @param opt Optional to construct Option from
     * @return Option created from Optional
     */
    public static <T> OptionType<T> ofOptional(Higher<OptionalType.µ,T> optional){
        return widen(Option.ofOptional(OptionalType.narrow(optional)));
    }
    public static <T> OptionType<T> ofOptional(Optional<T> optional){
        return widen(Option.ofOptional(optional));
    }
    /**
     * Convert a Optional to a simulated HigherKindedType that captures Optional nature
     * and Optional element data type separately. Recover via @see OptionalType#narrow
     * 
     * If the supplied Optional implements OptionalType it is returned already, otherwise it
     * is wrapped into a Optional implementation that does implement OptionalType
     * 
     * @param Optional Optional to widen to a OptionalType
     * @return OptionType encoding HKT info about Optionals (converts Optional to a Option)
     */
    public static <T> OptionType<T> widen(final Optional<T> optional) {
        
        return new Box<>(Option.ofOptional(optional));
    }
    public static <C2,T> Higher<C2, Higher<OptionType.µ,T>> widen2(Higher<C2, OptionType<T>> nestedOption){
        //a functor could be used (if C2 is a functor / one exists for C2 type) instead of casting
        //cast seems safer as Higher<OptionType.µ,T> must be a StreamType
        return (Higher)nestedOption;
    }
    /**
     * Convert the HigherKindedType definition for a Optional into
     * 
     * @param Optional Type Constructor to convert back into narrowed type
     * @return Optional from Higher Kinded Type
     */
    public static <T> Optional<T> narrowOptional(final Higher<OptionType.µ, T> Optional) {
        
         return ((Box<T>)Optional).narrow().toJavaOptional();
        
    }
    /**
     * Convert the raw Higher Kinded Type for OptionType types into the OptionType type definition class
     * 
     * @param future HKT encoded list into a OptionType
     * @return OptionType
     */
    public static <T> OptionType<T> narrowK(final Higher<OptionType.µ, T> future) {
       return (OptionType<T>)future;
    }
    /**
     * Construct an Option which contains the provided (non-null) value.
     * Alias for @see {@link Option#of(Object)}
     * 
     * <pre>
     * {@code 
     * 
     *    Option<Integer> some = Option.just(10);
     *    some.map(i->i*2);
     * }
     * </pre>
     * 
     * @param value Value to wrap inside a Option
     * @return Option containing the supplied value
     */
    static <T> OptionType<T> some(final T value) {
        return of(value);
    }

    /**
     * Construct an Option which contains the provided (non-null) value
     * Equivalent to @see {@link Option#just(Object)}
     * <pre>
     * {@code 
     * 
     *    Option<Integer> some = Option.of(10);
     *    some.map(i->i*2);
     * }
     * </pre>
     * 
     * @param value Value to wrap inside a Option
     * @return Option containing the supplied value
     */
    static <T> OptionType<T> of(final T value) {
       return widen(Option.of(value));
    }


    /**
     * Convert a Option to a simulated HigherKindedType that captures Option nature
     * and Option element data type separately. Recover via @see OptionType#narrow
     * 
     * If the supplied Option implements OptionType it is returned already, otherwise it
     * is wrapped into a Option implementation that does implement OptionType
     * 
     * @param Option Option to widen to a OptionType
     * @return OptionType encoding HKT info about Options
     */
    public static <T> OptionType<T> widen(final Option<T> maybe) {
        if (maybe instanceof OptionType)
            return (OptionType<T>) maybe;
        return new Box<>(
                         maybe);
    }

    /**
     * Convert the HigherKindedType definition for a Option into
     * 
     * @param Option Type Constructor to convert back into narrowed type
     * @return OptionX from Higher Kinded Type
     */
    public static <T> Option<T> narrow(final Higher<OptionType.µ, T> maybe) {
        if (maybe instanceof Option)
            return (Option) maybe;
        //this code should be unreachable due to HKT type checker
        final Box<T> type = (Box<T>) maybe;
        return type.narrow();
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    static final class Box<T> implements OptionType<T> {

        
        private final Option<T> boxed;

        /**
         * @return This back as a OptionX
         */
        public Option<T> narrow() {
            return boxed;
        }

       

        public T get() {
            return boxed.get();
        }



        @Override
        public boolean isEmpty() {
           return boxed.isEmpty();
        }



        @Override
        public boolean isSingleValued() {
           return boxed.isSingleValued();
        }



        

        @Override
        public String stringPrefix() {
            return boxed.stringPrefix();
        }



        @Override
        public Iterator<T> iterator() {
           return boxed.iterator();
        }



        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if(!(obj instanceof Option))
                return false;
            Option other = (Option) obj;
            
            if (boxed == null) {
                if (other != null)
                    return false;
            } else if (!boxed.equals(other))
                return false;
            return true;
        }



        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((boxed == null) ? 0 : boxed.hashCode());
            return result;
        }



        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "OptionType[" + boxed + "]";
        }





       
  
        
      
    }


   

}
