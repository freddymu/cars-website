$(document).ready(function() {
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
    const colors = ['Red', 'Blue', 'Green', 'Yellow', 'Black', 'White', 'Silver', 'Gray'];

    // Fetch car data from the API
    function fetchCars(offset = 0, params = {}) {
        const queryString = Object.entries(params)
            .filter(([_, value]) => value !== '')
            .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
            .join('&');

        return fetch(`http://localhost:8080/api/cars?limit=${carsPerPage}&offset=${offset}&${queryString}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error('Network response was not ok');
                }
                return response.json();
            })
            .then(data => {
                totalRows = data.totalRows;
                return data.data;
            })
            .catch(error => {
                console.error('Error fetching car data:', error);
                return []; // Return an empty array in case of error
            });
    }

    // Fetch UI parameters from the API
    function fetchUIParams() {
        return fetch('https://localhost:8080/api/cars/ui-params')
            .then(response => {
                if (!response.ok) {
                    throw new Error('Network response was not ok');
                }
                return response.json();
            })
            .catch(error => {
                console.error('Error fetching UI parameters:', error);
                return {}; // Return an empty object in case of error
            });
    }

    // Initialize color filters
    function initColorFilters() {
        const colorFilters = $('#colorFilters');
        colors.forEach(color => {
            const colorBox = $('<div>')
                .addClass('w-6 h-6 rounded cursor-pointer')
                .css('background-color', color.toLowerCase())
                .attr('data-color', color)
                .on('click', function() {
                    $(this).toggleClass('ring-2 ring-soft-red');
                    applyFilters();
                });
            colorFilters.append(colorBox);
        });
    }

    // Render car list
    function renderCars() {
        const carList = $('#carList');
        carList.empty();

        filteredCars.forEach(car => {
            const carCard = $('<div>').addClass('bg-white p-4 rounded shadow');
            carCard.html(`
                <img src="${car.imageUrl || '/placeholder.svg?height=150&width=200'}" alt="${car.trimYear} ${car.make} ${car.model} ${car.trimName}" class="w-full h-32 object-cover mb-2 rounded">
                <h2 class="text-base font-semibold">${car.trimYear} ${car.make} ${car.model} ${car.trimName}</h2>
                <p class="text-sm">${car.trimDescription}</p>
                <div class="flex flex-wrap justify-between mt-2">
                    <div class="flex items-center mr-2 mb-2" title="Fuel Type">
                        <i class="fas fa-gas-pump text-soft-red mr-1"></i>
                        <span class="text-xs">${car.fuelType}</span>
                    </div>
                    <div class="flex items-center mr-2 mb-2" title="Transmission">
                        <i class="fas fa-cogs text-soft-red mr-1"></i>
                        <span class="text-xs">${car.transmission}</span>
                    </div>
                    <div class="flex items-center mr-2 mb-2" title="Body Type">
                        <i class="fas fa-car text-soft-red mr-1"></i>
                        <span class="text-xs">${car.bodyType}</span>
                    </div>
                    <div class="flex items-center mr-2 mb-2" title="Length">
                        <i class="fas fa-ruler-horizontal text-soft-red mr-1"></i>
                        <span class="text-xs">${car.length} in</span>
                    </div>
                    <div class="flex items-center mr-2 mb-2" title="Weight">
                        <i class="fas fa-weight-hanging text-soft-red mr-1"></i>
                        <span class="text-xs">${car.weight} lbs</span>
                    </div>
                </div>
                ${car.exteriorColors ? `<p class="text-sm mt-2">Colors: ${renderColorBoxes(car.exteriorColors)}</p>` : ''}
            `);
            carList.append(carCard);
        });

        updatePagination();
    }

    // Render color boxes
    function renderColorBoxes(colors) {
        if (!colors) return '';
        return colors.split(',').map(color => 
            `<span class="inline-block w-4 h-4 mr-1 rounded" style="background-color: ${color.trim().toLowerCase()};"></span>`
        ).join('');
    }

    // Update pagination
    function updatePagination() {
        const totalPages = Math.ceil(totalRows / carsPerPage);
        $('#pageInfo').text(`Page ${currentPage} of ${totalPages}`);
        $('#prevPage').prop('disabled', currentPage === 1);
        $('#nextPage').prop('disabled', currentPage === totalPages);
    }

    // Get filter parameters
    function getFilterParams() {
        const params = {};
        const searchTerm = $('#search').val().toLowerCase();
        if (searchTerm) params['search'] = searchTerm;

        const filterFields = ['make', 'model', 'bodyType', 'transmission', 'fuelType'];
        filterFields.forEach(field => {
            const value = $(`#${field}`).val();
            if (value) params[`filter[${field}]`] = value;
        });

        const rangeFields = ['trimYear', 'length', 'weight', 'velocity'];
        rangeFields.forEach(field => {
            const minValue = $(`#${field}Min`).val();
            const maxValue = $(`#${field}Max`).val();
            if (minValue) params[`filter[${field}][min]`] = minValue;
            if (maxValue) params[`filter[${field}][max]`] = maxValue;
        });

        const selectedColors = $('#colorFilters .ring-2').map(function() {
            return $(this).attr('data-color');
        }).get();
        if (selectedColors.length > 0) {
            params['filter[exteriorColors]'] = selectedColors.join(',');
        }

        const [sortField, sortOrder] = $('#sort').val().split(',');
        params[`sort[${sortField}]`] = sortOrder;

        return params;
    }

    // Apply filters
    function applyFilters() {
        const params = getFilterParams();
        currentPage = 1;
        fetchCars(0, params).then(data => {
            cars = data;
            filteredCars = [...cars];
            renderCars();
        });
    }

    // Download XML
    function downloadXML() {
        const params = getFilterParams();
        const queryString = Object.entries(params)
            .filter(([_, value]) => value !== '')
            .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
            .join('&');

        window.location.href = `http://localhost:8080/api/cars/xml?${queryString}`;
    }

    // Event listeners
    const debouncedSearch = debounce(applyFilters, 300);

    $('#search').on('input', debouncedSearch);
    $('#make, #model, #trimYearMin, #trimYearMax, #bodyType, #transmission, #fuelType, #lengthMin, #lengthMax, #weightMin, #weightMax, #velocityMin, #velocityMax').on('change', applyFilters);
    $('#sort').on('change', applyFilters);
    $('#prevPage').on('click', () => {
        if (currentPage > 1) {
            currentPage--;
            const offset = (currentPage - 1) * carsPerPage;
            const params = getFilterParams();
            fetchCars(offset, params).then(data => {
                cars = data;
                filteredCars = [...cars];
                renderCars();
            });
        }
    });
    $('#nextPage').on('click', () => {
        const totalPages = Math.ceil(totalRows / carsPerPage);
        if (currentPage < totalPages) {
            currentPage++;
            const offset = (currentPage - 1) * carsPerPage;
            const params = getFilterParams();
            fetchCars(offset, params).then(data => {
                cars = data;
                filteredCars = [...cars];
                renderCars();
            });
        }
    });
    $('#downloadXML').on('click', downloadXML);

    // Initialize
    fetchUIParams().then(uiParams => {
        // TODO: Process uiParams and populate dropdowns
        // This is a placeholder for processing the UI parameters
        // You should update this section to handle the actual response structure
        const dropdowns = {
            make: uiParams.makes || [],
            model: uiParams.models || [],
            bodyType: uiParams.bodyTypes || [],
            transmission: uiParams.transmissions || [],
            fuelType: uiParams.fuelTypes || []
        };

        Object.entries(dropdowns).forEach(([key, values]) => {
            const select = $(`#${key}`);
            values.forEach(value => select.append(`<option value="${value}">${value}</option>`));
        });

        initColorFilters();
        return fetchCars(0, {});
    }).then(data => {
        cars = data;
        filteredCars = [...cars];
        renderCars();
    });
});

