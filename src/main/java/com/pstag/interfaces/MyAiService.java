package com.pstag.interfaces;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

//@RegisterAiService(modelName="gpt-4o-mini-2024-07-18")
@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
// @RegisterAiService
@ApplicationScoped
public interface MyAiService {

    @SystemMessage("You are a professional poet")
    @UserMessage("""
                Write a poem about {topic}. The poem should be {lines} lines long. Then send this poem by email.
            """)
    String writeAPoem(String topic, int lines);

    // @SystemMessage("You are an expert data scientist")
    // @UserMessage("""
    //             I have multiple car entities with specific details. I need you to find the following information for each car entity:

    //             - Top Speed (Velocity) : The estimated top speed in kilometers per hour (km/h). 
    //             - Exterior Color Options (color) : Gather a list of possible exterior colors for each car entity. The list should include the normalized common name of the color (e.g., "White").
    //             - Google Image Search Keywords : For each exterior color, create a Google search keyword that can be used to find images of the car in that specific color. The format should include the trimYear, make, model, trimName, trimDescription, and color name (e.g., "2015 Acura ILX Premium Package 4dr Sedan (2.4L 4cyl 6M) White").
                
    //             Here is the car information I have:
    //             {listOfData}
                
    //             Requirements:
    //             For each car entity:

    //             Include in the JSON structure :
    //             - "id": The car's unique identifier.
    //             - "make": Car manufacturer.
    //             - "model": Car model.
    //             - "trimYear": Model year.
    //             - "trimName": Specific trim name.
    //             - "trimDescription": Description of the car trim.
    //             - "fuelType": Type of fuel used.
    //             - "transmission": Transmission details.
    //             - "bodyType": Body style (e.g., Sedan).
    //             - "color": An array of exterior colors (mandatory).
    //             - "length": Length of the vehicle.
    //             - "weight": Weight of the vehicle.
    //             - "velocity": Estimated top speed in km/h (mandatory).
    //             - "imageUrl": An array for any image URLs (can be empty for now).
    //             - "exteriorColors": An array of color objects where each object contains:
    //                 - "name": The normalized common name of the exterior color (mandatory).
    //                 - "googleImageSearchKeyword": The keyword for Google image search (mandatory).
                
    //             ---

    //             {response_schema}
    //         """)
    // CarResponse getCarInformation(String listOfData);
    
    @SystemMessage("You are an expert data scientist")
    @UserMessage("""
                I have multiple car entities with specific details. I need you to find the following information for each car entity:

                - Top Speed (Velocity) : The estimated top speed in kilometers per hour (km/h). 
                - Exterior Color Options (color) : Gather a list of possible exterior colors for each car entity. The list should include the normalized common name of the color (e.g., "White").
                - Google Image Search Keywords : For each exterior color, create a Google search keyword that can be used to find images of the car in that specific color. The format should include the trimYear, make, model, trimName, trimDescription, and color name (e.g., "2015 Acura ILX Premium Package 4dr Sedan (2.4L 4cyl 6M) White").
                
                Here is the car information I have:
                {listOfData}
                
                Requirements:
                For each car entity:

                Include in the JSON structure :
                - "id": The car's unique identifier.
                - "color": An array of exterior colors (mandatory).
                - "velocity": Estimated top speed in km/h (mandatory).
                - "exteriorColors": An array of color objects where each object contains:
                    - "name": The normalized common name of the exterior color (mandatory).
                    - "googleImageSearchKeyword": The keyword for Google image search (mandatory).
                
                ---

                Only response with JSON code and does nothing else.
            """)
    String getCarInformation(String listOfData);
}