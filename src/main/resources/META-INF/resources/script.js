$(document).ready(function () {
  function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  }

  let cars = [];
  let filteredCars = [];
  let currentPage = 1;
  const carsPerPage = 9;
  let totalRows = 0;
  let colors = [];
  let isInitFilterParams = true;

  // Fetch car data from the API
  function fetchCars(offset = 0, params = {}) {
    const queryString = Object.entries(params)
      .filter(([_, value]) => value !== "")
      .map(
        ([key, value]) =>
          `${encodeURIComponent(key)}=${encodeURIComponent(value)}`
      )
      .join("&");

    return fetch(
      `/api/cars?limit=${carsPerPage}&offset=${offset}&${queryString}`
    )
      .then((response) => {
        if (!response.ok) {
          throw new Error("Network response was not ok");
        }
        return response.json();
      })
      .then((data) => {
        totalRows = data.totalRows;
        return data.data;
      })
      .catch((error) => {
        console.error("Error fetching car data:", error);
        return []; // Return an empty array in case of error
      });
  }

  // Fetch UI parameters from the API
  function fetchUIParams() {
    return fetch("/api/cars/ui-params")
      .then((response) => {
        if (!response.ok) {
          throw new Error("Network response was not ok");
        }
        return response.json();
      })
      .catch((error) => {
        console.error("Error fetching UI parameters:", error);
        return {}; // Return an empty object in case of error
      });
  }

  // Initialize color filters
  function initColorFilters() {
    const colorFilters = $("#colorFilters");
    const colorsHexCode = {
      Black: "#000000",
      Blue: "#0000FF",
      Red: "#FF0000",
      Gray: "#808080",
      White: "#FFFFFF",
      Silver: "#C0C0C0",
      Green: "#008000",
      Grey: "#808080",
      Yellow: "#FFFF00",
      Brown: "#A52A2A",
      "Carbon Black": "#1C1C1C",
      "Pearl White": "#EAEAE0",
      "Metallic Grey": "#A7A8AA",
      Purple: "#800080",
      Tan: "#D2B48C",
      Orange: "#FFA500",
      Beige: "#F5F5DC",
      Gold: "#FFD700",
      Bronze: "#CD7F32",
    };

    colors.forEach((color) => {
      let hexCode = colorsHexCode[color];

      const colorBox = $("<div>")
        .addClass("w-6 h-6 rounded cursor-pointer")
        .css("background-color", hexCode ?? color.toLowerCase())
        .attr("data-color", color)
        .on("click", function () {
          $(this).toggleClass("ring-2 ring-soft-red");
          applyFilters();
        });
      colorFilters.append(colorBox);
    });
  }

  // Render car list
  function renderCars() {
    const carList = $("#carList");
    carList.empty();

    if (filteredCars.length === 0) {
      $("#emptyState").removeClass("hidden");
      $("#carList").addClass("hidden");
    } else {
      $("#emptyState").addClass("hidden");
      $("#carList").removeClass("hidden");

      filteredCars.forEach(async (car) => {
        const carCard = $("<div id='carList'>").addClass(
          "bg-white p-4 rounded shadow"
        );

        const imageUrls = car.imageUrl ?? [];

        const shuffledUrls = Object.entries(imageUrls).sort(
          () => 0.5 - Math.random()
        );

        let carouselItems = "";

        if (shuffledUrls.length !== 0) {
          carouselItems = shuffledUrls
            .map(
              ([color, url]) => `
        <div class="carousel-item">
            <img src="${url}" alt="${car.trimYear} ${car.make} ${car.model} ${car.trimName}" class="w-full h-32 object-cover mb-2 rounded">
        </div>
        `
            )
            .join("");
        } else {
          carouselItems = `
            <div class="carousel-item h-32 w-full flex justify-center items-center text-center">
              <div class="h-full flex justify-center items-center gap-2">  
                <i class="fas fa-spinner fa-spin text-4xl text-gray-500"></i>
                <span class="text-xs text-gray-500">Loading images...</span>
              </div>
            </div>
            `;
        }

        carCard.html(`                    
            <div class="relative">
                <div class="carousel" data-id="${car.id}">
                ${carouselItems}
                </div>
                <button data-id="${car.id}prevBtn" class="prev absolute top-1/2 left-0 transform -translate-y-1/2 bg-gray-800 text-white p-2 rounded-full">&#10094;</button>
                <button data-id="${car.id}nextBtn" class="next absolute top-1/2 right-0 transform -translate-y-1/2 bg-gray-800 text-white p-2 rounded-full">&#10095;</button>
            </div>
            <h2 class="text-base font-semibold">${car.trimYear} ${car.make} ${
          car.model
        } ${car.trimName}</h2>
            <p class="text-sm">${car.trimDescription}</p>
            <p class="text-sm">
                <i class="fas fa-cogs text-soft-red mr-1"></i>
                <span class="text-xs">${car.transmission}</span>
            </p>
            <div class="grid grid-cols-2 gap-2 mt-2">
                <div class="flex items-center" title="Fuel Type">
                    <i class="fas fa-gas-pump text-soft-red mr-1"></i>
                    <span class="text-xs">${car.fuelType}</span>
                </div>                    
                <div class="flex items-center" title="Body Type">
                    <i class="fas fa-car text-soft-red mr-1"></i>
                    <span class="text-xs">${car.bodyType}</span>
                </div>
                <div class="flex items-center" title="Length">
                    <i class="fas fa-ruler-horizontal text-soft-red mr-1"></i>
                    <span class="text-xs">${car.length} in</span>
                </div>
                <div class="flex items-center" title="Weight">
                    <i class="fas fa-weight-hanging text-soft-red mr-1"></i>
                    <span class="text-xs">${car.weight} lbs</span>
                </div>
                <div class="flex items-center" title="Velocity">
                    <i class="fas fa-tachometer-alt text-soft-red mr-1"></i>
                    <span class="text-xs">${car.velocity} kph</span>
                </div>
            </div>
            ${
              car.color
                ? `<div class="flex mt-4 w-full">${renderColorBoxes(
                    car.color
                  )}</div>`
                : ""
            }
            `);
        carList.append(carCard);

        if (
          !car.imageUrl ||
          (Array.isArray(car.imageUrl) && car.imageUrl.length === 0)
        ) {
          fetch(`/api/cars/image/${car.id}`)
            .then((response) => {
              if (!response.ok) {
                throw new Error("Network response was not ok");
              }
              return response.json();
            })
            .then((response) => {
                const shuffledUrls = Object.entries(response.data)
                .filter(([_, url]) => url !== "")
                .sort(() => 0.5 - Math.random());

              const carousel = $(`.carousel[data-id="${car.id}"]`);

              if (shuffledUrls.length === 0) {
                $(`button[data-id="${car.id}prevBtn"]`).hide();
                $(`button[data-id="${car.id}nextBtn"]`).hide();
  
                carousel.html(
                  `<div class="carousel-item h-32 w-full flex justify-center items-center text-center">
                    <div class="h-full flex justify-center items-center gap-2">
                      <span class="text-xs text-red-500">
                        Failed to load images from Google Search!
                      </span>
                    </div>
                  </div>`
                );
                return;
              }
              carousel.html(
                shuffledUrls.map(
                  ([key, value]) =>
                    `<div class="carousel-item">
                      <img src="${value}" alt="${car.trimYear} ${car.make} ${car.model} ${car.trimName}" class="w-full h-32 object-cover mb-2 rounded"></img>
                    </div>`
                )
              );
            });
        }
      });
    }

    updatePagination();
  }

  // Render color boxes
  function renderColorBoxes(colors) {
    if (!colors) return "";
    return colors
      .map(
        (color) =>
          `<div class="flex flex-col items-center mr-2 mb-2">
                <span class="inline-block w-4 h-4 rounded" style="background-color: ${color
                  .trim()
                  .toLowerCase()};"></span>
                <span class="text-xs mt-1">${color.trim()}</span>
            </div>`
      )
      .join("");
  }

  // Update pagination
  function updatePagination() {
    const totalPages = Math.ceil(totalRows / carsPerPage);
    $("#pageInfo").text(`Page ${currentPage} of ${totalPages}`);
    $("#prevPage").prop("disabled", currentPage === 1);
    $("#nextPage").prop("disabled", currentPage === totalPages);
  }

  // Get filter parameters
  function getFilterParams() {
    const params = {};
    const searchTerm = $("#search").val().toLowerCase();
    if (searchTerm) params["search"] = searchTerm;

    const filterFields = [
      "make",
      "model",
      "bodyType",
      "transmission",
      "fuelType",
    ];
    filterFields.forEach((field) => {
      const value = $(`#${field}`).val();
      if (value) params[`filter[${field}]`] = value;
    });

    const rangeFields = ["trimYear", "length", "weight", "velocity"];
    rangeFields.forEach((field) => {
      const minValue = $(`#${field}Min`).val();
      const maxValue = $(`#${field}Max`).val();
      if (minValue || maxValue) {
        params[`filter[${field}]`] = `between(${minValue || 0},${
          maxValue || 99999
        })`;
      }
    });

    const selectedColors = $("#colorFilters .ring-2")
      .map(function () {
        return $(this).attr("data-color");
      })
      .get();
    if (selectedColors.length > 0) {
      params["filter[color]"] = selectedColors.join(",");
    }

    const selectedSort = $("#sort").val();
    if (selectedSort) {
      const [sortField, sortOrder] = selectedSort.split(",");
      params[`sort[${sortField}]`] = sortOrder;
    }

    return params;
  }

  // create function to setFilterParams from URL query string
  function initFilterParams() {
    const url = new URL(window.location);
    const searchParams = new URLSearchParams(url.search);

    searchParams.forEach((value, key) => {
      if (key === "search") {
        $("#search").val(value);
      } else if (key.startsWith("filter")) {
        const match = /\[(.*?)\]/.exec(key);
        const field = match ? match[1] : null;
        $(`#${field}`).val(value);

        if (field === "make") {
          // trigger event change
          $(`#${field}`).trigger("change");
        }

        if (value.indexOf("between") >= 0) {
          const [min, max] = value.match(/\d+/g);
          $(`#${field}Min`).val(min);
          $(`#${field}Max`).val(max >= 99999 ? "" : max);
        }

        if (field === "color") {
          value.split(",").forEach((color) => {
            $(`#colorFilters [data-color="${color}"]`).addClass(
              "ring-2 ring-soft-red"
            );
          });
        }
      } else if (key.startsWith("sort")) {
        const match = /\[(.*?)\]/.exec(key);
        const field = match ? match[1] : null;
        $("#sort").val(`${field},${value}`);
      }
    });
  }

  function updateURLWithFilter(params) {
    const url = new URL(window.location);
    const queryString = Object.entries(params)
      .map(([key, value]) => `${key}=${value}`)
      .join("&");
    window.history.pushState({}, "", `${url.pathname}?${queryString}`);
  }

  // Apply filters
  function applyFilters(event) {
    const triggerById = event?.target?.id;

    if (triggerById === "make") {
      $("#model").val("");
    }

    if (isInitFilterParams) return;
    const params = getFilterParams();
    updateURLWithFilter(params);
    currentPage = 1;
    fetchCars(0, params).then((data) => {
      cars = data;
      filteredCars = [...cars];
      renderCars();
    });
  }

  // Download XML
  function downloadXML() {
    const params = getFilterParams();
    const queryString = Object.entries(params)
      .filter(([_, value]) => value !== "")
      .map(
        ([key, value]) =>
          `${encodeURIComponent(key)}=${encodeURIComponent(value)}`
      )
      .join("&");

    if (
      queryString.indexOf("search") === -1 &&
      queryString.indexOf("filter") === -1
    ) {
      alert("Please apply filters or search to download XML. ");
      return;
    }

    window.location.href = `/api/cars/xml?${queryString}`;
  }

  // Event listeners
  const debouncedSearch = debounce(applyFilters, 300);

  $("#search").on("input", debouncedSearch);
  $(
    "#make, #model, #trimYearMin, #trimYearMax, #bodyType, #transmission, #fuelType, #lengthMin, #lengthMax, #weightMin, #weightMax, #velocityMin, #velocityMax"
  ).on("change", applyFilters);
  $("#sort").on("change", applyFilters);
  $("#prevPage").on("click", () => {
    if (currentPage > 1) {
      currentPage--;
      const offset = (currentPage - 1) * carsPerPage;
      const params = getFilterParams();
      fetchCars(offset, params).then((data) => {
        cars = data;
        filteredCars = [...cars];
        renderCars();
      });
    }
  });
  $("#nextPage").on("click", () => {
    const totalPages = Math.ceil(totalRows / carsPerPage);
    if (currentPage < totalPages) {
      currentPage++;
      const offset = (currentPage - 1) * carsPerPage;
      const params = getFilterParams();
      fetchCars(offset, params).then((data) => {
        cars = data;
        filteredCars = [...cars];
        renderCars();
      });
    }
  });
  $("#downloadXML").on("click", downloadXML);

  // Add these event listeners at the end of the $(document).ready function
  $("#filterToggle").on("click", function () {
    $("#sidebar").toggleClass("hidden");
  });

  $("#closeSidebar").on("click", function () {
    $("#sidebar").addClass("hidden");
  });

  $("#resetFilters").on("click", function () {
    // Reset all form inputs
    $('input[type="text"], input[type="number"], select').val("");
    $("#colorFilters .ring-2").removeClass("ring-2 ring-soft-red");
    applyFilters();
  });

  // Initialize
  fetchUIParams().then((uiParams) => {
    const dropdowns = {
      make: uiParams.data.makers || [],
      bodyType: uiParams.data.bodyTypes || [],
      transmission: uiParams.data.transmissions || [],
      fuelType: uiParams.data.fuelTypes || [],
    };

    colors = uiParams.data.colors || [];

    Object.entries(dropdowns).forEach(([key, values]) => {
      const select = $(`#${key}`);

      if (key === "make") {
        select.on("change", function () {
          const make = $(this).val();
          const models = uiParams.data.makersAndModels[make] || [];
          const modelSelect = $("#model");
          modelSelect.empty();
          modelSelect.append(`<option value="">All</option>`);
          models.forEach((model) =>
            modelSelect.append(`<option value="${model}">${model}</option>`)
          );
        });
      }

      values.forEach((value) =>
        select.append(`<option value="${value}">${value}</option>`)
      );
    });

    initColorFilters();
    initFilterParams();
    isInitFilterParams = false;
    applyFilters();
  });
});
