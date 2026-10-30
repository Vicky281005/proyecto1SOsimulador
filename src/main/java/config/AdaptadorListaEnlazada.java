package config;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Adaptador de Gson para serializar y deserializar {@link ListaEnlazada} propia
 * como un arreglo JSON estándar ({@code [...]}), sin utilizar colecciones de {@code java.util}.
 */
public class AdaptadorListaEnlazada implements JsonSerializer<ListaEnlazada<?>>, JsonDeserializer<ListaEnlazada<?>> {

    @Override
    public JsonElement serialize(ListaEnlazada<?> src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray array = new JsonArray();
        if (src != null) {
            Iterador<?> it = src.iterador();
            while (it.haySiguiente()) {
                array.add(context.serialize(it.siguiente()));
            }
        }
        return array;
    }

    @Override
    public ListaEnlazada<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        ListaEnlazada<Object> lista = new ListaEnlazada<>();
        if (json == null || !json.isJsonArray()) {
            return lista;
        }

        Type tipoElemento = Object.class;
        if (typeOfT instanceof ParameterizedType pt) {
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs != null && typeArgs.length > 0) {
                tipoElemento = typeArgs[0];
            }
        }

        JsonArray array = json.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            Object elemento = context.deserialize(array.get(i), tipoElemento);
            lista.agregar(elemento);
        }
        return lista;
    }
}
